package org.smartregister.multitenant;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.evernote.android.job.JobManager;

import org.smartregister.CoreLibrary;
import org.smartregister.P2POptions;
import org.smartregister.R;
import org.smartregister.exception.AppResetException;
import org.smartregister.multitenant.check.EventClientSyncedCheck;
import org.smartregister.multitenant.check.SettingsSyncedCheck;
import org.smartregister.multitenant.check.StructureSyncedCheck;
import org.smartregister.multitenant.check.TaskSyncedCheck;
import org.smartregister.multitenant.exception.PreResetAppOperationException;
import org.smartregister.multitenant.executor.CoreLibraryExecutors;
import org.smartregister.multitenant.ui.ResetAppDialog;
import org.smartregister.p2p.P2PLibrary;
import org.smartregister.p2p.model.AppDatabase;
import org.smartregister.util.Utils;
import org.smartregister.view.activity.DrishtiApplication;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Enumeration;

import timber.log.Timber;

/**
 * Created by Ephraim Kigamba - nek.eam@gmail.com on 09-04-2020.
 */
public class ResetAppUtil {

    private DrishtiApplication application;
    private CoreLibraryExecutors coreLibraryExecutors;
    private ArrayList<PreResetAppCheck> preResetAppChecks = new ArrayList<>();
    private ResetAppDialog resetAppDialog;
    private boolean resetCancelled = false;

    public ResetAppUtil(@NonNull DrishtiApplication drishtiApplication) {
        this.application = drishtiApplication;
        coreLibraryExecutors = new CoreLibraryExecutors();
        preResetAppChecks.add(new EventClientSyncedCheck());
        preResetAppChecks.add(new SettingsSyncedCheck());
        preResetAppChecks.add(new StructureSyncedCheck());
        preResetAppChecks.add(new TaskSyncedCheck());
    }

    public void startResetProcess(@NonNull AppCompatActivity activity) {
        resetCancelled = false;
        // Show some UI here to display the reset progress
        resetAppDialog = ResetAppDialog.newInstance();
        resetAppDialog.show(activity.getSupportFragmentManager(), "rest-app-dialog");
        resetAppDialog.setOnCancelListener((dialogInterface) -> {
            showProgressText(activity.getString(R.string.cancelling));
            resetCancelled = true;
        });
        resetAppDialog.showText(activity.getString(R.string.cancelling_services));
        JobManager.create(application).cancelAll();

        resetAppDialog.showText(activity.getString(R.string.performing_data_checks));

        if (!resetCancelled) {
            performPreResetChecks();
        }
    }

    public void performPreResetChecks() {
        // Should be handled in the background
        coreLibraryExecutors.diskIO()
                .execute(() -> {
                    try {
                        for (PreResetAppCheck preResetAppCheck : preResetAppChecks) {
                            if (!preResetAppCheck.isCheckOk(application)) {
                                if (resetCancelled) {
                                    cancelProcess();
                                    return;
                                }

                                performPreResetOperations(preResetAppCheck);
                            }
                        }

                        if (resetCancelled) {
                            cancelProcess();
                            return;
                        }

                        coreLibraryExecutors.mainThread()
                                .execute(() -> {
                                    resetAppDialog.showText("Clearing application data...");
                                });

                        if (resetCancelled) {
                            cancelProcess();
                            return;
                        }

                        performResetOperations();
                        coreLibraryExecutors.mainThread()
                                .execute(() -> {
                                    // Done here, what should we do
                                    application.logoutCurrentUser();
                                    resetAppDialog.dismiss();
                                });

                    } catch (PreResetAppOperationException | AppResetException e) {
                        Timber.e(e);
                    }
                });
    }

    public void performResetOperations() throws AppResetException {
        clearSqCipherDb();
        clearP2PDb();
        clearAllPrivateKeyEntries();
        clearSharedPreferences();
    }

    protected void clearP2PDb() {
        P2POptions p2POptions = CoreLibrary.getInstance().getP2POptions();
        if (p2POptions != null && p2POptions.isEnableP2PLibrary()) {
            AppDatabase roomP2PDb = P2PLibrary.getInstance().getDb();
            roomP2PDb.clearAllTables();
            Utils.deleteRoomDb(application.getApplicationContext(), roomP2PDb.getOpenHelper().getDatabaseName());
        }
    }

    public void performPreResetOperations(@NonNull PreResetAppCheck preResetAppCheck) throws PreResetAppOperationException {
        preResetAppCheck.performPreResetAppOperations(application);
    }

    protected boolean clearSharedPreferences() {
        SharedPreferences sharedPreferences = application.getContext().allSharedPreferences().getPreferences();
        if (sharedPreferences != null) {
            return sharedPreferences.edit().clear().commit();
        }

        return false;
    }

    protected boolean clearSqCipherDb() {
        return application.getRepository().deleteRepository();
    }

    protected void clearAllPrivateKeyEntries() throws AppResetException {
        KeyStore keyStore = application.getContext().userService().getKeyStore();
        if (keyStore != null) {
            try {
                Enumeration<String> keystoreEnumeration = keyStore.aliases();
                while (keystoreEnumeration.hasMoreElements()) {
                    String keyStoreAlias = keystoreEnumeration.nextElement();
                    keyStore.deleteEntry(keyStoreAlias);
                }
            } catch (KeyStoreException ex) {
                Timber.e(ex);
                throw new AppResetException(ex.getMessage(), ex);
            }
        }
    }

    public boolean addPreResetAppCheck(@NonNull PreResetAppCheck preResetAppCheck) {
        if (!preResetAppChecks.contains(preResetAppCheck)) {
            return preResetAppChecks.add(preResetAppCheck);
        }

        return false;
    }

    public boolean removePreResetAppCheck(@NonNull PreResetAppCheck preResetAppCheck) {
        return preResetAppChecks.remove(preResetAppCheck);
    }

    @Nullable
    public PreResetAppCheck removePreResetAppCheck(@NonNull String checkName) {
        for (PreResetAppCheck preResetAppCheck: preResetAppChecks) {
            if (checkName.equals(preResetAppCheck.getUniqueName())) {
                if(removePreResetAppCheck(preResetAppCheck)) {
                    return preResetAppCheck;
                }

                break;
            }
        }

        return null;
    }

    protected void cancelProcess() {
        if (resetAppDialog != null) {
            resetAppDialog.dismiss();
        }
    }


    @AnyThread
    public void showProgressText(@NonNull String progressText) {
        if (resetCancelled) {
            return;
        }

        coreLibraryExecutors.mainThread()
                .execute(() -> {
                    resetAppDialog.showText(progressText);
                });

    }

    protected void killRunningServices() {
        ArrayList<ActivityManager.RunningServiceInfo> runningServices = getRunningServicesForApp();

        for (ActivityManager.RunningServiceInfo runningServiceInfo: runningServices) {
            try {
                application.stopService(new Intent(application, Class.forName(runningServiceInfo.service.getClassName())));
            } catch (ClassNotFoundException e) {
                Timber.e(e);
            }
        }
    }

    private ArrayList<ActivityManager.RunningServiceInfo> getRunningServicesForApp() {
        ArrayList<ActivityManager.RunningServiceInfo> runningServices = new ArrayList<>();
        ActivityManager manager = (ActivityManager) application.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (service.clientPackage.contains(application.getPackageName())) {
                runningServices.add(service);
            }
        }

        return runningServices;
    }
}

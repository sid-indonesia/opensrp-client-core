package org.smartregister.view.activity;

import android.app.Activity;
import android.content.Intent;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.Snackbar;
import android.support.design.widget.SnackbarContentLayout;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.barcode.Barcode;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.powermock.reflect.Whitebox;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;
import org.smartregister.AllConstants;
import org.smartregister.AllConstants.BARCODE;
import org.smartregister.BaseRobolectricUnitTest;
import org.smartregister.CoreLibrary;
import org.smartregister.R;
import org.smartregister.domain.FetchStatus;
import org.smartregister.receiver.SyncStatusBroadcastReceiver;
import org.smartregister.service.ZiggyService;
import org.smartregister.shadows.ShadowSnackBar;
import org.smartregister.util.AppExecutors;
import org.smartregister.view.activity.mock.BaseRegisterActivityMock;
import org.smartregister.view.contract.BaseRegisterContract;
import org.smartregister.view.fragment.BaseRegisterFragment;
import org.smartregister.view.viewpager.OpenSRPViewPager;

import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.smartregister.view.activity.BaseRegisterActivity.BASE_REG_POSITION;

/**
 * Created by samuelgithengi on 6/30/20.
 */
public class BaseRegisterActivityTest extends BaseRobolectricUnitTest {

    private BaseRegisterActivity activity;

    private ActivityController<BaseRegisterActivityMock> controller;

    @Mock
    private ZiggyService ziggyService;

    @Mock
    private BaseRegisterFragment fragment;

    @Mock
    private OpenSRPViewPager mPager;

    @Mock
    private Barcode barcode;

    @Before
    public void setUp() {
        Whitebox.setInternalState(CoreLibrary.getInstance().context(), "ziggyService", ziggyService);
        controller = Robolectric.buildActivity(BaseRegisterActivityMock.class).create().start().resume();
        activity = controller.get();
    }

    @Test
    public void testOnCreate() {

        assertNotNull(activity.presenter);
        assertNotNull(activity.getRegisterFragment());
        assertNotNull(Whitebox.getInternalState(activity, "mPagerAdapter"));
    }

    @Test
    public void testRegisterBottomNavigation() {
        BottomNavigationView bottomNavigationView = activity.findViewById(R.id.bottom_navigation);
        assertNotNull(bottomNavigationView);
        assertEquals(5, bottomNavigationView.getMenu().size());

        MenuItem item = bottomNavigationView.getMenu().findItem(R.string.action_me);
        assertEquals(activity.getString(R.string.me), item.getTitle());
        assertNotNull(item.getIcon());
        assertNotNull(Whitebox.getInternalState(bottomNavigationView, "selectedListener"));
    }

    @Test
    public void testOnDestroy() {
        BaseRegisterContract.Presenter presenter = activity.presenter;
        activity.onDestroy();
        verify(presenter).onDestroy(false);
    }

    @Test
    public void testOnCreateOptionsMenu() {
        BottomNavigationView bottomNavigationView = activity.findViewById(R.id.bottom_navigation);
        activity = spy(activity);
        assertTrue(activity.onCreateOptionsMenu(bottomNavigationView.getMenu()));
        verify(activity).onCreateOptionsMenu(any());
        verifyNoMoreInteractions(activity);
    }

    @Test
    public void testOnBackPressedInvokesRegisterFragmentBackPressed() {
        SyncStatusBroadcastReceiver.init(activity);
        activity = controller.visible().get();
        activity = spy(activity);
        when(activity.findFragmentByPosition(0)).thenReturn(fragment);
        activity.onBackPressed();
        verify(fragment).onBackPressed();
        BottomNavigationView bottomNavigationView = activity.findViewById(R.id.bottom_navigation);
        assertEquals(R.id.action_clients, bottomNavigationView.getSelectedItemId());
    }

    @Test
    public void testOnBackPressedSwitchesToBaseFragment() {
        SyncStatusBroadcastReceiver.init(activity);
        Whitebox.setInternalState(activity, "currentPage", 1);
        Whitebox.setInternalState(activity, "mPager", mPager);
        activity = controller.visible().get();
        activity.onBackPressed();
        verify(mPager, atLeastOnce()).setCurrentItem(BASE_REG_POSITION, false);
    }

    @Test
    @Config(shadows = {ShadowSnackBar.class})
    public void testDisplaySyncNotification() {
        activity.displaySyncNotification();
        Snackbar snackbar = ShadowSnackBar.getLatestSnackbar();
        Snackbar.SnackbarLayout layout = (Snackbar.SnackbarLayout) snackbar.getView();
        assertEquals(((TextView) ((SnackbarContentLayout) layout.getChildAt(0)).getChildAt(0)).getText(), activity.getString(R.string.manual_sync_triggered));
        assertEquals(Snackbar.LENGTH_LONG, snackbar.getDuration());
        assertTrue(snackbar.isShown());
    }

    @Test
    public void testDisplayToast() {
        activity.displayToast(R.string.manual_sync_triggered);
        assertEquals(Toast.LENGTH_LONG, ShadowToast.getLatestToast().getDuration());
        assertEquals(activity.getString(R.string.manual_sync_triggered), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testDisplayToastWithString() {
        activity.displayToast("Message");
        assertEquals(Toast.LENGTH_LONG, ShadowToast.getLatestToast().getDuration());
        assertEquals("Message", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testDisplayShortToast() {
        activity.displayShortToast(R.string.manual_sync_triggered);
        assertEquals(Toast.LENGTH_SHORT, ShadowToast.getLatestToast().getDuration());
        assertEquals(activity.getString(R.string.manual_sync_triggered), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testGetDefaultOptionsProvider() {
        assertNull(activity.getDefaultOptionsProvider());
    }

    @Test
    public void testGetNavBarOptionsProvider() {
        assertNull(activity.getNavBarOptionsProvider());
    }

    @Test
    public void testClientsProvider() {
        assertNull(activity.clientsProvider());
    }

    @Test
    public void testSetupViewsDoesNothing() {
        activity = spy(activity);
        activity.setupViews();
        verify(activity).setupViews();
        verifyNoMoreInteractions(activity);
    }

    @Test
    public void testOnResumption() {
        activity.onResumption();
        verify(activity.presenter).registerViewConfigurations(null);
    }

    @Test
    public void testOnInitializationDoesNothing() {
        activity = spy(activity);
        activity.onInitialization();
        verify(activity).onInitialization();
        verifyNoMoreInteractions(activity);
    }

    @Test
    public void testOnActivityResultWithBarcodeResult() {
        Intent intent = new Intent();
        intent.putExtra(BARCODE.BARCODE_KEY, barcode);
        String code = UUID.randomUUID().toString();
        Whitebox.setInternalState(barcode, "displayValue", code);
        Whitebox.setInternalState(activity, "mBaseFragment", fragment);
        activity.onActivityResult(BARCODE.BARCODE_REQUEST_CODE, Activity.RESULT_OK, intent);
        verify(fragment).onQRCodeSucessfullyScanned(code);
        verify(fragment).setSearchTerm(code);

    }

    @Test
    public void testOnActivityResultInvokesOnActivityResultExtended() {
        Whitebox.setInternalState(activity, "mBaseFragment", fragment);
        activity = spy(activity);
        activity.onActivityResult(AllConstants.FORM_SUCCESSFULLY_SUBMITTED_RESULT_CODE, Activity.RESULT_OK, null);
        verify(fragment, never()).onQRCodeSucessfullyScanned(anyString());
        verify(fragment, never()).setSearchTerm(anyString());
        verify(activity).onActivityResultExtended(AllConstants.FORM_SUCCESSFULLY_SUBMITTED_RESULT_CODE, Activity.RESULT_OK, null);

    }

    @Test
    public void refreshListInvokesFragmentRefresh() {
        activity = spy(activity);
        when(activity.findFragmentByPosition(0)).thenReturn(fragment);
        activity.refreshList(FetchStatus.fetched);
        verify(fragment).refreshListView();
    }

    @Test
    public void refreshListOnWorkerThreadInvokesFragmentRefresh() {
        activity = spy(activity);
        when(activity.findFragmentByPosition(0)).thenReturn(fragment);
        AppExecutors appExecutors = new AppExecutors(Executors.newSingleThreadExecutor(), Executors.newSingleThreadExecutor(), Executors.newSingleThreadExecutor());
        Whitebox.setInternalState(activity,"appExecutors",appExecutors);
        appExecutors.diskIO().execute(() -> {
            activity.refreshList(FetchStatus.fetched);
        });
        verify(fragment, timeout(ASYNC_TIMEOUT)).refreshListView();
    }
}

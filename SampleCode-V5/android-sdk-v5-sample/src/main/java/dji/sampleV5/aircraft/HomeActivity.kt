package dji.sampleV5.aircraft

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import dji.sdk.keyvalue.value.common.CameraLensType
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager.AvailableCameraUpdatedListener
import dji.v5.network.DJINetworkManager
import dji.v5.network.IDJINetworkStatusListener
import dji.v5.utils.common.JsonUtil
import dji.v5.utils.common.LogPath
import dji.v5.utils.common.LogUtils
import dji.v5.ux.accessory.RTKStartServiceHelper.startRtkService
import dji.v5.ux.cameracore.widget.autoexposurelock.AutoExposureLockWidget
import dji.v5.ux.cameracore.widget.cameracontrols.CameraControlsWidget
import dji.v5.ux.cameracore.widget.cameracontrols.lenscontrol.LensControlWidget
import dji.v5.ux.cameracore.widget.focusexposureswitch.FocusExposureSwitchWidget
import dji.v5.ux.cameracore.widget.focusmode.FocusModeWidget
import dji.v5.ux.cameracore.widget.fpvinteraction.FPVInteractionWidget
import dji.v5.ux.core.base.SchedulerProvider.io
import dji.v5.ux.core.base.SchedulerProvider.ui
import dji.v5.ux.core.communication.BroadcastValues
import dji.v5.ux.core.communication.GlobalPreferenceKeys
import dji.v5.ux.core.communication.ObservableInMemoryKeyedStore
import dji.v5.ux.core.communication.UXKeys
import dji.v5.ux.core.extension.hide
import dji.v5.ux.core.extension.toggleVisibility
import dji.v5.ux.core.panel.systemstatus.SystemStatusListPanelWidget
import dji.v5.ux.core.panel.topbar.TopBarPanelWidget
import dji.v5.ux.core.util.CameraUtil
import dji.v5.ux.core.util.DataProcessor
import dji.v5.ux.core.util.ViewUtil
import dji.v5.ux.core.widget.fpv.FPVStreamSourceListener
import dji.v5.ux.core.widget.fpv.FPVWidget
import dji.v5.ux.core.widget.hsi.HorizontalSituationIndicatorWidget
import dji.v5.ux.core.widget.hsi.PrimaryFlightDisplayWidget
import dji.v5.ux.core.widget.setting.SettingWidget
import dji.v5.ux.gimbal.GimbalFineTuneWidget
import dji.v5.ux.map.MapWidget
import dji.v5.ux.map.MapWidget.OnMapReadyListener
import dji.v5.ux.mapkit.core.maps.DJIMap
import dji.v5.ux.training.simulatorcontrol.SimulatorControlWidget
import dji.v5.ux.training.simulatorcontrol.SimulatorControlWidget.UIState.VisibilityUpdated
import dji.v5.ux.visualcamera.CameraNDVIPanelWidget
import dji.v5.ux.visualcamera.CameraVisiblePanelWidget
import dji.v5.ux.visualcamera.zoom.FocalZoomWidget
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.Consumer
import java.util.concurrent.TimeUnit

/**
 * Displays a sample layout of widgets similar to that of the various DJI apps.
 */
class HomeActivity : AppCompatActivity() {
    //region Fields
    private val TAG: String? = LogUtils.getTag(this)

    protected var primaryFpvWidget: FPVWidget? = null
    protected var fpvInteractionWidget: FPVInteractionWidget? = null
    protected var secondaryFPVWidget: FPVWidget? = null
    protected var systemStatusListPanelWidget: SystemStatusListPanelWidget? = null
    protected var simulatorControlWidget: SimulatorControlWidget? = null
    protected var lensControlWidget: LensControlWidget? = null
    protected var autoExposureLockWidget: AutoExposureLockWidget? = null
    protected var focusModeWidget: FocusModeWidget? = null
    protected var focusExposureSwitchWidget: FocusExposureSwitchWidget? = null
    protected var cameraControlsWidget: CameraControlsWidget? = null
    protected var horizontalSituationIndicatorWidget: HorizontalSituationIndicatorWidget? = null
    protected var pfvFlightDisplayWidget: PrimaryFlightDisplayWidget? = null
    protected var ndviCameraPanel: CameraNDVIPanelWidget? = null
    protected var visualCameraPanel: CameraVisiblePanelWidget? = null
    protected var focalZoomWidget: FocalZoomWidget? = null
    protected var settingWidget: SettingWidget? = null
    protected var mapWidget: MapWidget? = null
    protected var topBarPanel: TopBarPanelWidget? = null
    protected var fpvParentView: ConstraintLayout? = null
    private var mDrawerLayout: DrawerLayout? = null
    private var gimbalAdjustDone: TextView? = null
    private var gimbalFineTuneWidget: GimbalFineTuneWidget? = null
    private var lastDevicePosition: ComponentIndexType? = ComponentIndexType.UNKNOWN
    private var lastLensType: CameraLensType? = CameraLensType.UNKNOWN


    private var compositeDisposable: CompositeDisposable? = null
    private val cameraSourceProcessor: DataProcessor<CameraSource?> =
        DataProcessor.create<CameraSource?>(
            CameraSource(
                ComponentIndexType.UNKNOWN,
                CameraLensType.UNKNOWN
            )
        )
    private val networkStatusListener = IDJINetworkStatusListener { isNetworkAvailable: Boolean ->
        if (isNetworkAvailable) {
            LogUtils.d(TAG, "isNetworkAvailable=" + true)
            startRtkService(false)
        }
    }
    private val availableCameraUpdatedListener: AvailableCameraUpdatedListener =
        object : AvailableCameraUpdatedListener {
            override fun onAvailableCameraUpdated(availableCameraList: MutableList<ComponentIndexType?>) {
                runOnUiThread(Runnable { updateFPVWidgetSource(availableCameraList) })
            }

            override fun onCameraStreamEnableUpdate(cameraStreamEnableMap: MutableMap<ComponentIndexType?, Boolean?>) {
                //
            }
        }

    //endregion
    //region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity_layout)
        fpvParentView = findViewById<ConstraintLayout?>(R.id.fpv_holder)
        mDrawerLayout = findViewById<DrawerLayout?>(R.id.root_view)
        topBarPanel = findViewById<TopBarPanelWidget?>(R.id.panel_top_bar)
        settingWidget = topBarPanel!!.settingWidget
        primaryFpvWidget = findViewById<FPVWidget?>(R.id.widget_primary_fpv)
        fpvInteractionWidget = findViewById<FPVInteractionWidget?>(R.id.widget_fpv_interaction)
        secondaryFPVWidget = findViewById<FPVWidget?>(R.id.widget_secondary_fpv)
        systemStatusListPanelWidget =
            findViewById<SystemStatusListPanelWidget?>(R.id.widget_panel_system_status_list)
        simulatorControlWidget =
            findViewById<SimulatorControlWidget?>(R.id.widget_simulator_control)
        lensControlWidget = findViewById<LensControlWidget?>(R.id.widget_lens_control)
        ndviCameraPanel = findViewById<CameraNDVIPanelWidget?>(R.id.panel_ndvi_camera)
        visualCameraPanel = findViewById<CameraVisiblePanelWidget?>(R.id.panel_visual_camera)
        autoExposureLockWidget =
            findViewById<AutoExposureLockWidget?>(R.id.widget_auto_exposure_lock)
        focusModeWidget = findViewById<FocusModeWidget?>(R.id.widget_focus_mode)
        focusExposureSwitchWidget =
            findViewById<FocusExposureSwitchWidget?>(R.id.widget_focus_exposure_switch)
        pfvFlightDisplayWidget =
            findViewById<PrimaryFlightDisplayWidget?>(R.id.widget_fpv_flight_display_widget)
        focalZoomWidget = findViewById<FocalZoomWidget?>(R.id.widget_focal_zoom)
        cameraControlsWidget = findViewById<CameraControlsWidget?>(R.id.widget_camera_controls)
        horizontalSituationIndicatorWidget =
            findViewById<HorizontalSituationIndicatorWidget?>(R.id.widget_horizontal_situation_indicator)
        gimbalAdjustDone = findViewById<TextView?>(R.id.fpv_gimbal_ok_btn)
        gimbalFineTuneWidget =
            findViewById<GimbalFineTuneWidget?>(R.id.setting_menu_gimbal_fine_tune)
        mapWidget = findViewById<MapWidget?>(R.id.widget_map)

        initClickListener()
        MediaDataCenter.getInstance().getCameraStreamManager()
            .addAvailableCameraUpdatedListener(availableCameraUpdatedListener)
        primaryFpvWidget!!.setOnFPVStreamSourceListener(object : FPVStreamSourceListener {
            override fun onStreamSourceUpdated(devicePosition: ComponentIndexType, lensType: CameraLensType) {
                cameraSourceProcessor.onNext(
                    CameraSource(
                        devicePosition, lensType
                    )
                )
            }
        })

        //小surfaceView放置在顶部，避免被大的遮挡
        secondaryFPVWidget!!.setSurfaceViewZOrderOnTop(true)
        secondaryFPVWidget!!.setSurfaceViewZOrderMediaOverlay(true)


        mapWidget!!.initMapLibreMap(getApplicationContext(), OnMapReadyListener { map: DJIMap? ->
            val uiSetting = map!!.getUiSettings()
            if (uiSetting != null) {
                uiSetting.setZoomControlsEnabled(false) //hide zoom widget
            }
        })
        mapWidget!!.onCreate(savedInstanceState)
        getWindow().setBackgroundDrawable(ColorDrawable(Color.BLACK))

        //实现RTK监测网络，并自动重连机制
        DJINetworkManager.getInstance().addNetworkStatusListener(networkStatusListener)
    }

    private fun isGimableAdjustClicked(broadcastValues: BroadcastValues?) {
        if (mDrawerLayout!!.isDrawerOpen(GravityCompat.END)) {
            mDrawerLayout!!.closeDrawers()
        }
        horizontalSituationIndicatorWidget!!.setVisibility(View.GONE)
        if (gimbalFineTuneWidget != null) {
            gimbalFineTuneWidget!!.setVisibility(View.VISIBLE)
        }
    }

    private fun initClickListener() {
        secondaryFPVWidget!!.setOnClickListener(View.OnClickListener { v: View? -> swapVideoSource() })

        if (settingWidget != null) {
            settingWidget!!.setOnClickListener(View.OnClickListener { v: View? -> toggleRightDrawer() })
        }

        // Setup top bar state callbacks
        val systemStatusWidget = topBarPanel!!.systemStatusWidget
        if (systemStatusWidget != null) {
            systemStatusWidget.setOnClickListener(View.OnClickListener { v: View? -> systemStatusListPanelWidget!!.toggleVisibility() })
        }

        val simulatorIndicatorWidget = topBarPanel!!.simulatorIndicatorWidget
        if (simulatorIndicatorWidget != null) {
            simulatorIndicatorWidget.setOnClickListener(View.OnClickListener { v: View? -> simulatorControlWidget!!.toggleVisibility() })
        }
        gimbalAdjustDone!!.setOnClickListener(View.OnClickListener { view: View? ->
            horizontalSituationIndicatorWidget!!.setVisibility(View.VISIBLE)
            if (gimbalFineTuneWidget != null) {
                gimbalFineTuneWidget!!.setVisibility(View.GONE)
            }
        })
    }

    private fun toggleRightDrawer() {
        mDrawerLayout!!.openDrawer(GravityCompat.END)
    }


    override fun onDestroy() {
        super.onDestroy()
        mapWidget!!.onDestroy()
        MediaDataCenter.getInstance().getCameraStreamManager()
            .removeAvailableCameraUpdatedListener(availableCameraUpdatedListener)
        DJINetworkManager.getInstance().removeNetworkStatusListener(networkStatusListener)
    }

    override fun onResume() {
        super.onResume()
        mapWidget!!.onResume()
        compositeDisposable = CompositeDisposable()
        compositeDisposable!!.add(
            systemStatusListPanelWidget!!.closeButtonPressed()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer { pressed: Boolean? ->
                    if (pressed == true) {
                        systemStatusListPanelWidget!!.hide()
                    }
                })
        )
        compositeDisposable!!.add(
            simulatorControlWidget!!.getUIStateUpdates()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer { simulatorControlWidgetState: SimulatorControlWidget.UIState? ->
                    if (simulatorControlWidgetState is VisibilityUpdated) {
                        if (simulatorControlWidgetState.isVisible) {
                            hideOtherPanels(simulatorControlWidget)
                        }
                    }
                })
        )
        compositeDisposable!!.add(
            cameraSourceProcessor.toFlowable()
                .observeOn(io())
                .throttleLast(500, TimeUnit.MILLISECONDS)
                .subscribeOn(io())
                .subscribe { result -> {
                    runOnUiThread(Runnable {
                        onCameraSourceUpdated(
                            result!!.devicePosition, result.lensType
                        )
                    })
                } }
        )
        compositeDisposable!!.add(
            ObservableInMemoryKeyedStore.getInstance()
                .addObserver(UXKeys.create(GlobalPreferenceKeys.GIMBAL_ADJUST_CLICKED))
                .observeOn(ui())
                .subscribe(Consumer { broadcastValues: BroadcastValues? ->
                    this.isGimableAdjustClicked(
                        broadcastValues
                    )
                })
        )
        ViewUtil.setKeepScreen(this, true)
    }

    override fun onPause() {
        if (compositeDisposable != null) {
            compositeDisposable!!.dispose()
            compositeDisposable = null
        }
        mapWidget!!.onPause()
        super.onPause()
        ViewUtil.setKeepScreen(this, false)
    }

    //endregion
    private fun hideOtherPanels(widget: View?) {
        val panels = arrayOf<View>(
            simulatorControlWidget!!
        )

        for (panel in panels) {
            if (widget !== panel) {
                panel.setVisibility(View.GONE)
            }
        }
    }

    private fun updateFPVWidgetSource(availableCameraList: MutableList<ComponentIndexType?>?) {
        LogUtils.i(TAG, JsonUtil.toJson<ComponentIndexType?>(availableCameraList))
        if (availableCameraList == null) {
            return
        }

        val cameraList = ArrayList<ComponentIndexType?>(availableCameraList)

        //没有数据
        if (cameraList.isEmpty()) {
            secondaryFPVWidget!!.setVisibility(View.GONE)
            return
        }

        //仅一路数据
        if (cameraList.size == 1) {
            primaryFpvWidget!!.updateVideoSource(availableCameraList.get(0)!!)
            secondaryFPVWidget!!.setVisibility(View.GONE)
            return
        }

        //大于两路数据
        val primarySource = getSuitableSource(cameraList, ComponentIndexType.LEFT_OR_MAIN)
        primaryFpvWidget!!.updateVideoSource(primarySource)
        cameraList.remove(primarySource)

        val secondarySource = getSuitableSource(cameraList, ComponentIndexType.FPV)
        secondaryFPVWidget!!.updateVideoSource(secondarySource)

        secondaryFPVWidget!!.setVisibility(View.VISIBLE)
    }

    private fun getSuitableSource(
        cameraList: MutableList<ComponentIndexType?>,
        defaultSource: ComponentIndexType
    ): ComponentIndexType {
        if (cameraList.contains(ComponentIndexType.LEFT_OR_MAIN)) {
            return ComponentIndexType.LEFT_OR_MAIN
        } else if (cameraList.contains(ComponentIndexType.RIGHT)) {
            return ComponentIndexType.RIGHT
        } else if (cameraList.contains(ComponentIndexType.UP)) {
            return ComponentIndexType.UP
        } else if (cameraList.contains(ComponentIndexType.PORT_1)) {
            return ComponentIndexType.PORT_1
        } else if (cameraList.contains(ComponentIndexType.PORT_2)) {
            return ComponentIndexType.PORT_2
        } else if (cameraList.contains(ComponentIndexType.PORT_3)) {
            return ComponentIndexType.PORT_4
        } else if (cameraList.contains(ComponentIndexType.PORT_4)) {
            return ComponentIndexType.PORT_4
        } else if (cameraList.contains(ComponentIndexType.VISION_ASSIST)) {
            return ComponentIndexType.VISION_ASSIST
        }
        return defaultSource
    }

    private fun onCameraSourceUpdated(
        devicePosition: ComponentIndexType,
        lensType: CameraLensType
    ) {
        LogUtils.i(LogPath.SAMPLE, "onCameraSourceUpdated", devicePosition, lensType)
        if (devicePosition == lastDevicePosition && lensType == lastLensType) {
            return
        }
        lastDevicePosition = devicePosition
        lastLensType = lensType
        updateViewVisibility(devicePosition, lensType)
        updateInteractionEnabled()
        //如果无需使能或者显示的，也就没有必要切换了。
        if (fpvInteractionWidget!!.isInteractionEnabled()) {
            fpvInteractionWidget!!.updateCameraSource(devicePosition, lensType)
        }
        if (lensControlWidget!!.getVisibility() == View.VISIBLE) {
            lensControlWidget!!.updateCameraSource(devicePosition, lensType)
        }
        if (ndviCameraPanel!!.getVisibility() == View.VISIBLE) {
            ndviCameraPanel!!.updateCameraSource(devicePosition, lensType)
        }
        if (visualCameraPanel!!.getVisibility() == View.VISIBLE) {
            visualCameraPanel!!.updateCameraSource(devicePosition, lensType)
        }
        if (autoExposureLockWidget!!.getVisibility() == View.VISIBLE) {
            autoExposureLockWidget!!.updateCameraSource(devicePosition, lensType)
        }
        if (focusModeWidget!!.getVisibility() == View.VISIBLE) {
            focusModeWidget!!.updateCameraSource(devicePosition, lensType)
        }
        if (focusExposureSwitchWidget!!.getVisibility() == View.VISIBLE) {
            focusExposureSwitchWidget!!.updateCameraSource(devicePosition, lensType)
        }
        if (cameraControlsWidget!!.getVisibility() == View.VISIBLE) {
            cameraControlsWidget!!.updateCameraSource(devicePosition, lensType)
        }
        if (focalZoomWidget!!.getVisibility() == View.VISIBLE) {
            focalZoomWidget!!.updateCameraSource(devicePosition, lensType)
        }
        if (horizontalSituationIndicatorWidget!!.getVisibility() == View.VISIBLE) {
            horizontalSituationIndicatorWidget!!.updateCameraSource(devicePosition, lensType)
        }
    }

    private fun updateViewVisibility(
        devicePosition: ComponentIndexType?,
        lensType: CameraLensType?
    ) {
        //Only shows under fpv
        pfvFlightDisplayWidget!!.setVisibility(if (CameraUtil.isFPVTypeView(devicePosition)) View.VISIBLE else View.INVISIBLE)

        //fpv It is not shown
        lensControlWidget!!.setVisibility(if (CameraUtil.isFPVTypeView(devicePosition)) View.INVISIBLE else View.VISIBLE)
        ndviCameraPanel!!.setVisibility(if (CameraUtil.isFPVTypeView(devicePosition)) View.INVISIBLE else View.VISIBLE)
        visualCameraPanel!!.setVisibility(if (CameraUtil.isFPVTypeView(devicePosition)) View.INVISIBLE else View.VISIBLE)
        autoExposureLockWidget!!.setVisibility(if (CameraUtil.isFPVTypeView(devicePosition)) View.INVISIBLE else View.VISIBLE)
        focusModeWidget!!.setVisibility(if (CameraUtil.isFPVTypeView(devicePosition)) View.INVISIBLE else View.VISIBLE)
        focusExposureSwitchWidget!!.setVisibility(if (CameraUtil.isFPVTypeView(devicePosition)) View.INVISIBLE else View.VISIBLE)
        cameraControlsWidget!!.setVisibility(if (CameraUtil.isFPVTypeView(devicePosition)) View.INVISIBLE else View.VISIBLE)
        focalZoomWidget!!.setVisibility(if (CameraUtil.isFPVTypeView(devicePosition)) View.INVISIBLE else View.VISIBLE)
        horizontalSituationIndicatorWidget!!.setSimpleModeEnable(
            CameraUtil.isFPVTypeView(
                devicePosition
            )
        )

        //Shows only under partial lens
        ndviCameraPanel!!.setVisibility(if (CameraUtil.isSupportForNDVI(lensType)) View.VISIBLE else View.INVISIBLE)
    }

    /**
     * Swap the video sources of the FPV and secondary FPV widgets.
     */
    private fun swapVideoSource() {
        val primarySource = primaryFpvWidget!!.widgetModel.getCameraIndex()
        val secondarySource = secondaryFPVWidget!!.widgetModel.getCameraIndex()
        //两个source都存在的情况下才进行切换
        if (primarySource != ComponentIndexType.UNKNOWN && secondarySource != ComponentIndexType.UNKNOWN) {
            primaryFpvWidget!!.updateVideoSource(secondarySource)
            secondaryFPVWidget!!.updateVideoSource(primarySource)
        }
    }

    private fun updateInteractionEnabled() {
        fpvInteractionWidget!!.setInteractionEnabled(!CameraUtil.isFPVTypeView(primaryFpvWidget!!.widgetModel.getCameraIndex()))
    }

    private class CameraSource(var devicePosition: ComponentIndexType, var lensType: CameraLensType)

    override fun onBackPressed() {
        if (mDrawerLayout!!.isDrawerOpen(GravityCompat.END)) {
            mDrawerLayout!!.closeDrawers()
        } else {
            super.onBackPressed()
        }
    }
}
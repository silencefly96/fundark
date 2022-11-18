package com.silencefly96.module_base.base

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.silencefly96.module_base.R
import java.util.*

/**
 * 基类 Activity
 *
 * 支持默认 Toolbar、沉浸状态栏、全屏、禁止旋转、Toast简化、页面跳转简化、权限申请等功能
 *
 *
 * @author fdk
 * @date 2021/07/16
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
abstract class BaseActivity : AppCompatActivity(), IPermissionHelper {

    /**
     * 自定义 ToolBar 主标题，
     */
    var mToolbarTitle: TextView? = null

    /**
     * 自定义 ToolBar 子标题
     */
    var mToolbarSubTitle: TextView? = null

    /**
     * 自定义 ToolBar
     */
    var mToolbar: Toolbar? = null

    /**
     * 是否使用默认自定义的 toolbar
     */
    var isDefaultToolbar = true

    /**
     * 是否显示 toolbar 返回键
     */
    var isShowBacking = true

    /**
     * 是否沉浸状态栏
     */
    var isSteepStatusBar = false

    /**
     * 是否全屏
     */
    var isFullScreen = false

    /**
     * 是否禁止旋转屏幕，默认竖屏显示
     */
    var isAllowScreenRotate = false

    /**
     * 日志输出标志
     */
    @Suppress("PropertyName")
    protected val TAG: String? = this.javaClass.simpleName


    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "BaseActivity-->onCreate()")

        //绑定视图，而不是布局，适合配合 view binding 使用
        val mView = bindView()

        //当前 Activity 渲染的视图View
        val mContextView: View = mView ?:
                //推荐使用 view binding，但依然可以使用 布局 ID 绑定
                LayoutInflater.from(this).inflate(bindLayout(), null)

        //设置全屏
        if (isFullScreen) {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }

        //设置沉浸状态栏
        if (isSteepStatusBar) {
            steepStatusBar()
        }

        //设置活动布局
        setContentView(mContextView)

        //设置屏幕旋转
        if (!isAllowScreenRotate) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // 默认自定义 TOOLBAR 相关
        if (isDefaultToolbar) {

            //几个控件
            mToolbar = findViewById(R.id.toolbar)
            mToolbarTitle = findViewById(R.id.toolbar_title)
            mToolbarSubTitle = findViewById(R.id.toolbar_subtitle)

            if (mToolbar != null) {
                //将Toolbar显示到界面
                setSupportActionBar(mToolbar)

                //设置 Toolbar，顶部 padding 设置为状态栏高度
                mToolbar!!.setPadding(
                    mToolbar!!.paddingLeft,
                    statusBarHeight,
                    mToolbar!!.paddingRight,
                    mToolbar!!.paddingBottom
                )
            }

            if (mToolbarTitle != null) {
                //getTitle()的值是activity的android:label属性值
                mToolbarTitle!!.text = title
                //设置默认的标题不显示
                val actionBar = supportActionBar
                actionBar?.setDisplayShowTitleEnabled(false)
            }
        }

        //初始化传入参数
        initData(intent)

        //处理其他逻辑
        doBusiness(this)
    }

    /**
     * 绑定视图
     *
     * @return 绑定视图
     */
    open fun bindView(): View?{
        return null
    }

    /**
     * 绑定布局
     *
     * @return 布局 id
     */
    fun bindLayout(): Int {
        return 0
    }

    /**
     * 初始化参数，不写 onCreate 可以用这个(不强制)
     *
     * @param intent 传入的 intent
     */
    open fun initData(intent: Intent?) {}

    /**
     * 绑定视图，不写 onCreate 可以用这个(不强制)
     *
     * @return 绑定视图
     */
    open fun doBusiness(context: Context) {}

    /**
     * 沉浸状态栏
     */
    @SuppressLint("ObsoleteSdkInt")
    private fun steepStatusBar() {
        //沉浸式状态栏，方式一
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            // 沉浸式状态栏
//            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
//            // 透明状态栏
//            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
//        }

        //沉浸式状态栏，方式二
        if (Build.VERSION.SDK_INT >= 21) {
            // 沉浸式状态栏
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

            // 透明状态栏
            window.statusBarColor = Color.TRANSPARENT
        }
    }

    /**
     * 利用反射获取状态栏高度
     * @return
     * 状态栏高度
     */
    val statusBarHeight: Int
        get() {
            var result = 0
            //获取状态栏高度的资源id
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                result = resources.getDimensionPixelSize(resourceId)
            }
            return result
        }

    /**
     * 页面跳转
     *
     * @param clz 跳转 activity 类名
     */
    fun startActivity(clz: Class<*>?) {
        startActivity(Intent(this@BaseActivity, clz))
    }

    /**
     * 携带数据的页面跳转
     *
     * @param clz 跳转 activity 类名
     * @param bundle 数据
     */
    fun startActivity(clz: Class<*>?, bundle: Bundle?) {
        val intent = Intent()
        intent.setClass(this, clz!!)
        if (bundle != null) {
            intent.putExtras(bundle)
        }
        startActivity(intent)
    }

    /**
     * 携带数据且需要返回的页面跳转
     *
     * @param cls 跳转 activity 类名
     * @param bundle 数据
     * @param requestCode 请求码
     */
    fun startActivityForResult(
        cls: Class<*>?, bundle: Bundle?,
        requestCode: Int
    ) {
        val intent = Intent()
        intent.setClass(this, cls!!)
        if (bundle != null) {
            intent.putExtras(bundle)
        }
        startActivityForResult(intent, requestCode)
    }

    /**
     * 简化 Toast
     * @param msg Toast 文字字符串
     */
    protected fun showToast(msg: String?) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    /**
     * 简化 Toast
     * @param id 文字资源ID
     */
    protected fun showToast(id: Int) {
        Toast.makeText(this, id, Toast.LENGTH_SHORT).show()
    }

    /**
     * 设置新的 toolbar，替代默认toolbar
     * @param mToolbar
     * toolbar
     */
    fun setToolbar(mToolbar: Toolbar?) {
        this.mToolbar = mToolbar
        if (this.mToolbar != null) {
            //将Toolbar显示到界面
            setSupportActionBar(this.mToolbar)
            this.mToolbar!!.setPadding(
                this.mToolbar!!.paddingLeft,
                statusBarHeight,
                this.mToolbar!!.paddingRight,
                this.mToolbar!!.paddingBottom
            )
        }
    }

    /**
     * 版本号小于21的后退按钮图片
     */
    private fun showBack() {
        //setNavigationIcon必须在setSupportActionBar(toolbar);方法后面加入
        mToolbar!!.setNavigationIcon(R.drawable.ic_arrow_back)
        mToolbar!!.setNavigationOnClickListener { onBackPressed() }
    }

    /**
     * 私有权限接口
     */
    override var mPermissionListener: IPermissionHelper.PermissionListener? = null


    /**
     * 申请权限结果
     * @param requestCode 请求码
     * @param permissions 权限组
     * @param grantResults 结果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        //交由权限接口内的方法进行处理
        onRunTimePermissionResult(requestCode, permissions, grantResults)
    }

    override fun onRestart() {
        super.onRestart()
        Log.d(TAG, "onRestart()")
    }

    override fun onStart() {
        super.onStart()
        //设置返回键显示，避免在 setSupportActionBar 前调用
        if (null != mToolbar && isShowBacking) {
            showBack()
        }

        Log.d(TAG, "onStart()")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume()")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause()")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop()")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")
    }
}
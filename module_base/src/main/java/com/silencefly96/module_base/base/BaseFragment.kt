package com.silencefly96.module_base.base

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * 基类 Fragment
 *
 * 支持默认Toast简化、页面跳转简化、权限申请等功能
 *
 * @author fdk
 * @date 2021/07/16
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class BaseFragment : Fragment(), IPermissionHelper {

    /**
     * 当前Fragment渲染的视图View
     */
    var mContextView: View? = null

    /**
     * 贴附的 activity
     */
    var mActivity: FragmentActivity? = null

    /**
     * 日志输出标志
     */
    @Suppress("PropertyName")
    val TAG: String? = this.javaClass.simpleName

    override fun onAttach(context: Context) {
        Log.d(TAG, "BaseFragment-->onAttach()")
        super.onAttach(context)
        mActivity = activity
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "BaseFragment-->onCreateView()")

        //绑定视图，而不是布局
        val mView = bindView(inflater, container)
        mContextView = mView ?: inflater.inflate(bindLayout(), container, false)

        //初始化控件
        initData(activity?.intent)

        //开启业务
        doBusiness(mActivity)

        return mContextView
    }

    /**
     * 绑定视图
     *
     * @return view
     */
    open fun bindView(inflater: LayoutInflater, container: ViewGroup?): View?{
        return null
    }

    /**
     * 绑定布局
     *
     * @return 布局ID
     */
     open fun bindLayout(): Int{
         return 0
     }

    /**
     * 初始化数据，不写 onCreateView 可以用这个(不强制)
     *
     * @param intent 传入 activity 的数据
     */
    open fun initData(intent: Intent?) {}


    /**
     * 业务操作，不写 onCreateView 可以用这个(不强制)
     *
     * @param context 上下文对象
     */
    open fun doBusiness(context: Context?) {}

    /**
     * 页面跳转
     *
     * @param clz 跳转 activity 类名
     */
    fun startActivity(clz: Class<*>?) {
        startActivity(Intent(mActivity, clz))
    }

    /**
     * 携带数据的页面跳转
     *
     * @param clz 跳转 activity 类名
     * @param bundle 数据
     */
    fun startActivity(clz: Class<*>?, bundle: Bundle?) {
        val intent = Intent()
        intent.setClass(mActivity!!, clz!!)
        if (bundle != null) {
            intent.putExtras(bundle)
        }
        startActivity(intent)
    }

    /**
     * 含有Bundle通过Class打开编辑界面
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
        intent.setClass(mActivity!!, cls!!)
        if (bundle != null) {
            intent.putExtras(bundle)
        }
        startActivityForResult(intent, requestCode)
    }

    /**
     * 简化 Toast
     * @param msg 字符串
     */
    protected fun showToast(msg: String?) {
        Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show()
    }

    /**
     * 简化 Toast
     * @param id 字符ID
     */
    protected fun showToast(id: Int) {
        Toast.makeText(mActivity, id, Toast.LENGTH_SHORT).show()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "BaseFragment-->onCreate()")
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.d(TAG, "BaseFragment-->onActivityCreated()")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "BaseFragment-->onStart()")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "BaseFragment-->onResume()")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "BaseFragment-->onPause()")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "BaseFragment-->onStop()")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "BaseFragment-->onDestroyView")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BaseFragment-->onDestroy")
    }

    override fun onDetach() {
        super.onDetach()
        Log.d(TAG, "BaseFragment-->onDetach")
    }
}
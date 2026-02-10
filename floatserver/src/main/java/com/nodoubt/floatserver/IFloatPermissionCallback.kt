package com.nodoubt.floatserver


/**
 * Created by yy on 2020/6/13.
 * function: 权限申请回调
 */
interface IFloatPermissionCallback {

    fun onPermissionResult(granted: Boolean)
}
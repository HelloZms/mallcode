package com.mls.library.net.okhttp.listener;

/**
 * Description: 业务逻辑层真正处理的地方，包括java层异常和业务层异常
 * Create By: MLS Co,Ltd
 */

public interface DisposeDataListener {

	/**
	 * 请求成功回调事件处理
	 */
	void onSuccess(Object responseObj);

	/**
	 * 请求失败回调事件处理
	 */
	void onFailure(Object reasonObj);

}

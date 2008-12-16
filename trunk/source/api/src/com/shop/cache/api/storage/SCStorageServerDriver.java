/*
 * Copyright 2008 SHOP.COM
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shop.cache.api.storage;

import com.shop.cache.api.server.SCLoggable;

/**
 * interface between the storage instance and the server
 *
 * @author Jordan Zimmerman
 */
public interface SCStorageServerDriver extends SCLoggable
{
	/**
	 * Storage instance is telling the server about an internal exception
	 *
	 * @param e the exception
	 */
	public void 		handleException(Exception e);

	/**
	 * Set the error state. IMPORTANT: Setting the error state will cause a shutdown of the non-monitor server
	 *
	 * @param errorState new error
	 */
	public void 		setErrorState(String errorState);

	/**
	 * The storage instance is telling the server to remove the given object
	 *
	 * @param key key of the object
	 */
	public void 		remove(String key);
}

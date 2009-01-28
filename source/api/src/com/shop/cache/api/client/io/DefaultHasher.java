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
package com.shop.cache.api.client.io;

import java.util.List;

/**
 * The default hash implementation
 *
 * @author Jordan Zimmerman
*/
class DefaultHasher implements SCHasher
{
	@Override
	public int keyToIndex(String key, List<SCClientManager> managers)
	{
		return Math.abs(key.hashCode()) % managers.size();
	}
}
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

import com.shop.cache.api.common.SCClientServerCommon;

/**
 * Interface for marking a manager. The purpose is so that a single client
 * can't be passed as a manager to certain APIs
 *
 * @author Jordan Zimmerman
 */
public interface SCManager extends SCClientServerCommon
{
}

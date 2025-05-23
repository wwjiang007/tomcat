/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.el.util;

/**
 * Utilities for handling Throwable and Exceptions.
 */
/*
 * Copied from o.a.t.u.ExceptionUtils
 */
public class ExceptionUtils {

    /**
     * Checks whether the supplied Throwable is one that needs to be rethrown and swallows all others.
     *
     * @param t the Throwable to check
     */
    public static void handleThrowable(Throwable t) {
        if (t instanceof StackOverflowError) {
            // Swallow silently - it should be recoverable
            return;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // All other instances of Throwable will be silently swallowed
    }


    /**
     * NO-OP method provided to enable simple preloading of this class. Since the class is used extensively in error
     * handling, it is prudent to preload it to avoid any failure to load this class masking the true problem during
     * error handling.
     */
    public static void preload() {
        // NO-OP
    }
}

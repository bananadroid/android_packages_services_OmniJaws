#
# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_STATIC_JAVA_LIBRARIES := prebuilt-jsoup \
	prebuilt-okhttp \
	prebuilt-okio
LOCAL_JAVA_LIBRARIES := org.apache.http.legacy
LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_PACKAGE_NAME := OmniJaws
LOCAL_MODULE_TAGS := optional
LOCAL_SDK_VERSION := system_current
LOCAL_REQUIRED_MODULES := privapp_whitelist_org.omnirom.omnijaws-ext.xml
#LOCAL_DEX_PREOPT := false
include $(BUILD_PACKAGE)

include $(CLEAR_VARS)
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := prebuilt-jsoup
LOCAL_SRC_FILES := libs/jsoup-1.11.3.jar
LOCAL_UNINSTALLABLE_MODULE := true
LOCAL_SDK_VERSION := current
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := prebuilt-okhttp
LOCAL_SRC_FILES := libs/okhttp-3.11.0.jar
LOCAL_UNINSTALLABLE_MODULE := true
LOCAL_SDK_VERSION := current
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE := prebuilt-okio
LOCAL_SRC_FILES := libs/okio-1.14.0.jar
LOCAL_UNINSTALLABLE_MODULE := true
LOCAL_SDK_VERSION := current
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := privapp_whitelist_org.omnirom.omnijaws-ext.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions
LOCAL_SRC_FILES := $(LOCAL_MODULE)
include $(BUILD_PREBUILT)

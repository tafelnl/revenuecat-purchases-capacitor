package com.revenuecat.purchases.capacitor

import android.app.Activity
import android.util.Log
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.Store
import com.revenuecat.purchases.common.PlatformInfo
import com.revenuecat.purchases.hybridcommon.ErrorContainer
import com.revenuecat.purchases.hybridcommon.OnResult
import com.revenuecat.purchases.hybridcommon.OnResultAny
import com.revenuecat.purchases.hybridcommon.OnResultList
import com.revenuecat.purchases.hybridcommon.configure
import com.revenuecat.purchases.hybridcommon.getProductInfo
import com.revenuecat.purchases.hybridcommon.mappers.convertToMap
import com.revenuecat.purchases.hybridcommon.mappers.map
import com.revenuecat.purchases.hybridcommon.purchaseProduct
import com.revenuecat.purchases.hybridcommon.showInAppMessagesIfNeeded
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.InAppMessageType
import com.revenuecat.purchases.hybridcommon.canMakePayments as canMakePaymentsCommon
import com.revenuecat.purchases.hybridcommon.checkTrialOrIntroductoryPriceEligibility as checkTrialOrIntroductoryPriceEligibilityCommon
import com.revenuecat.purchases.hybridcommon.collectDeviceIdentifiers as collectDeviceIdentifiersCommon
import com.revenuecat.purchases.hybridcommon.getAppUserID as getAppUserIDCommon
import com.revenuecat.purchases.hybridcommon.getCustomerInfo as getCustomerInfoCommon
import com.revenuecat.purchases.hybridcommon.getOfferings as getOfferingsCommon
import com.revenuecat.purchases.hybridcommon.invalidateCustomerInfoCache as invalidateCustomerInfoCacheCommon
import com.revenuecat.purchases.hybridcommon.isAnonymous as isAnonymousCommon
import com.revenuecat.purchases.hybridcommon.logIn as logInCommon
import com.revenuecat.purchases.hybridcommon.logOut as logOutCommon
import com.revenuecat.purchases.hybridcommon.purchasePackage as purchasePackageCommon
import com.revenuecat.purchases.hybridcommon.purchaseSubscriptionOption as purchaseSubscriptionOptionCommon
import com.revenuecat.purchases.hybridcommon.restorePurchases as restorePurchasesCommon
import com.revenuecat.purchases.hybridcommon.setAd as setAdCommon
import com.revenuecat.purchases.hybridcommon.setAdGroup as setAdGroupCommon
import com.revenuecat.purchases.hybridcommon.setAdjustID as setAdjustIDCommon
import com.revenuecat.purchases.hybridcommon.setAirshipChannelID as setAirshipChannelIDCommon
import com.revenuecat.purchases.hybridcommon.setAppsflyerID as setAppsflyerIDCommon
import com.revenuecat.purchases.hybridcommon.setAttributes as setAttributesCommon
import com.revenuecat.purchases.hybridcommon.setCampaign as setCampaignCommon
import com.revenuecat.purchases.hybridcommon.setCleverTapID as setCleverTapIDCommon
import com.revenuecat.purchases.hybridcommon.setCreative as setCreativeCommon
import com.revenuecat.purchases.hybridcommon.setDisplayName as setDisplayNameCommon
import com.revenuecat.purchases.hybridcommon.setEmail as setEmailCommon
import com.revenuecat.purchases.hybridcommon.setFBAnonymousID as setFBAnonymousIDCommon
import com.revenuecat.purchases.hybridcommon.setFinishTransactions as setFinishTransactionsCommon
import com.revenuecat.purchases.hybridcommon.setFirebaseAppInstanceID as setFirebaseAppInstanceIDCommon
import com.revenuecat.purchases.hybridcommon.setKeyword as setKeywordCommon
import com.revenuecat.purchases.hybridcommon.setLogHandler as setLogHandlerCommon
import com.revenuecat.purchases.hybridcommon.setLogLevel as setLogLevelCommon
import com.revenuecat.purchases.hybridcommon.setMediaSource as setMediaSourceCommon
import com.revenuecat.purchases.hybridcommon.setMixpanelDistinctID as setMixpanelDistinctIDCommon
import com.revenuecat.purchases.hybridcommon.setMparticleID as setMparticleIDCommon
import com.revenuecat.purchases.hybridcommon.setOnesignalID as setOnesignalIDCommon
import com.revenuecat.purchases.hybridcommon.setPhoneNumber as setPhoneNumberCommon
import com.revenuecat.purchases.hybridcommon.setProxyURLString as setProxyURLStringCommon
import com.revenuecat.purchases.hybridcommon.setPushToken as setPushTokenCommon
import com.revenuecat.purchases.hybridcommon.syncPurchases as syncPurchasesCommon

@Suppress("unused")
@CapacitorPlugin(name = "Purchases")
class PurchasesPlugin : Plugin() {
    private val customerInfoListeners = mutableListOf<String>()
    private val lastSeenCustomerInfo: CustomerInfo? = null
    private var logHandlerCallbackId: String? = null

    companion object {
        private const val PLATFORM_NAME = "capacitor"
        private const val PLUGIN_VERSION = "7.1.0-SNAPSHOT"

        private const val CUSTOMER_INFO_KEY = "customerInfo"
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun configure(call: PluginCall) {
        val apiKey = call.getStringOrReject("apiKey") ?: return
        val appUserID = call.getString("appUserID")
        val observerMode = call.getBoolean("observerMode")
        val useAmazon = call.getBoolean("useAmazon")
        val store = if (useAmazon == true) Store.AMAZON else Store.PLAY_STORE
        val platformInfo = PlatformInfo(PLATFORM_NAME, PLUGIN_VERSION)
        val shouldShowInAppMessages = call.getBoolean("shouldShowInAppMessagesAutomatically")
        configure(
            context.applicationContext,
            apiKey,
            appUserID,
            observerMode,
            platformInfo,
            store,
            shouldShowInAppMessagesAutomatically = shouldShowInAppMessages,
        )
        Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { customerInfo ->
            for (callbackId in customerInfoListeners) {
                bridge.getSavedCall(callbackId)?.resolveWithMap(customerInfo.map())
            }
        }
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setMockWebResults(call: PluginCall) {
        Log.e(
            "PurchasesCapacitor",
            "Cannot enable mock web results in Android."
        )
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setFinishTransactions(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val finishTransactions = call.getBooleanOrReject("finishTransactions") ?: return
        setFinishTransactionsCommon(finishTransactions)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setSimulatesAskToBuyInSandbox(call: PluginCall) {
        logNotSupportedFunctionalityInAndroid("setSimulatesAskToBuyInSandbox")
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_CALLBACK)
    fun addCustomerInfoUpdateListener(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        customerInfoListeners.add(call.callbackId)
        call.setKeepAlive(true)
        lastSeenCustomerInfo?.let { call.resolveWithMap(it.map()) }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun removeCustomerInfoUpdateListener(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val callbackIDToRemove = call.getStringOrReject("callbackID") ?: return
        val wasRemoved = customerInfoListeners.remove(callbackIDToRemove)
        bridge?.getSavedCall(callbackIDToRemove)?.setKeepAlive(false)
        call.resolveWithMap(mapOf("wasRemoved" to wasRemoved))
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun getOfferings(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        getOfferingsCommon(getOnResult(call))
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun getProducts(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val productIdentifiers = call.getArrayOrReject("productIdentifiers") ?: return
        val type = call.getString("type") ?: "SUBSCRIPTION"
        getProductInfo(productIdentifiers.toList(), type, object : OnResultList {
            override fun onReceived(map: List<Map<String, Any?>>) {
                val ret = JSObject()
                ret.put("products", convertListToJSArray(map))
                call.resolve(ret)
            }

            override fun onError(errorContainer: ErrorContainer) {
                rejectWithErrorContainer(call, errorContainer)
            }
        })
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun purchaseStoreProduct(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val storeProduct = call.getObjectOrReject("product") ?: return
        val productIdentifier = storeProduct.getStringOrReject(call, "identifier") ?: return
        val type = storeProduct.getStringOrReject(call, "productCategory") ?: return
        val presentedOfferingIdentifier = storeProduct.getString("presentedOfferingIdentifier")
        val optionalPurchaseParams = PurchaseOptionalInfoParams.fromCall(call)
        purchaseProduct(
            activity,
            productIdentifier,
            type,
            googleBasePlanId = null,
            optionalPurchaseParams.oldProductIdentifier,
            optionalPurchaseParams.prorationMode,
            optionalPurchaseParams.isPersonalizedPrice,
            presentedOfferingIdentifier,
            getOnResult(call),
        )
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun purchaseDiscountedProduct(call: PluginCall) {
        rejectNotSupportedInAndroid(call, "purchaseDiscountedProduct")
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun purchaseDiscountedPackage(call: PluginCall) {
        rejectNotSupportedInAndroid(call, "purchaseDiscountedPackage")
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun purchasePackage(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val packageToPurchase = call.getObjectOrReject("aPackage") ?: return
        val packageIdentifier = packageToPurchase.getStringOrReject(call, "identifier") ?: return
        val offeringIdentifier = packageToPurchase.getStringOrReject(call, "offeringIdentifier") ?: return
        val optionalPurchaseParams = PurchaseOptionalInfoParams.fromCall(call)

        purchasePackageCommon(
            activity,
            packageIdentifier,
            offeringIdentifier,
            optionalPurchaseParams.oldProductIdentifier,
            optionalPurchaseParams.prorationMode,
            optionalPurchaseParams.isPersonalizedPrice,
            getOnResult(call),
        )
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun purchaseSubscriptionOption(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val subscriptionOption = call.getObjectOrReject("subscriptionOption") ?: return
        val productId = subscriptionOption.getStringOrReject(call, "productId") ?: return
        val subscriptionOptionId = subscriptionOption.getStringOrReject(call, "id") ?: return
        val presentedOfferingIdentifier = subscriptionOption.getString("presentedOfferingIdentifier")
        val optionalPurchaseParams = PurchaseOptionalInfoParams.fromCall(call)

        purchaseSubscriptionOptionCommon(
            activity,
            productId,
            subscriptionOptionId,
            optionalPurchaseParams.oldProductIdentifier,
            optionalPurchaseParams.prorationMode,
            optionalPurchaseParams.isPersonalizedPrice,
            presentedOfferingIdentifier,
            getOnResult(call),
        )
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun restorePurchases(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        restorePurchasesCommon(getOnResult(call, CUSTOMER_INFO_KEY))
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun getAppUserID(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        call.resolveWithMap(mapOf("appUserID" to getAppUserIDCommon()))
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun logIn(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val appUserID = call.getStringOrReject("appUserID") ?: return
        logInCommon(appUserID, getOnResult(call))
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun logOut(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        logOutCommon(getOnResult(call, CUSTOMER_INFO_KEY))
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setLogLevel(call: PluginCall) {
        val logLevel = call.getStringOrReject("level") ?: return
        setLogLevelCommon(logLevel)
    }

    @PluginMethod(returnType = PluginMethod.RETURN_CALLBACK)
    fun setLogHandler(call: PluginCall) {
        bridge.getSavedCall(logHandlerCallbackId)?.setKeepAlive(false)
        call.setKeepAlive(true)
        logHandlerCallbackId = call.callbackId
        setLogHandlerCommon {
            call.resolve(convertMapToJSObject(it))
        }
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun getCustomerInfo(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        getCustomerInfoCommon(getOnResult(call, CUSTOMER_INFO_KEY))
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun syncPurchases(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        syncPurchasesCommon()
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun syncObserverModeAmazonPurchase(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val productID = call.getStringOrReject("productID") ?: return
        val receiptID = call.getStringOrReject("receiptID") ?: return
        val amazonUserID = call.getStringOrReject("amazonUserID") ?: return
        val isoCurrencyCode = call.getString("isoCurrencyCode")
        val price = call.getDouble("price")
        Purchases.sharedInstance.syncObserverModeAmazonPurchase(
            productID,
            receiptID,
            amazonUserID,
            isoCurrencyCode,
            price,
        )
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun enableAdServicesAttributionTokenCollection(call: PluginCall) {
        logNotSupportedFunctionalityInAndroid("enableAdServicesAttributionTokenCollection")
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun isAnonymous(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        call.resolveWithMap(mapOf("isAnonymous" to isAnonymousCommon()))
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun checkTrialOrIntroductoryPriceEligibility(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val productIdentifiers = call.getArrayOrReject("productIdentifiers") ?: return
        val eligibilityMap = checkTrialOrIntroductoryPriceEligibilityCommon(productIdentifiers.toList())
        call.resolveWithMap(eligibilityMap)
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun getPromotionalOffer(call: PluginCall) {
        logNotSupportedFunctionalityInAndroid("getPromotionalOffer")
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun invalidateCustomerInfoCache(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        invalidateCustomerInfoCacheCommon()
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun presentCodeRedemptionSheet(call: PluginCall) {
        logNotSupportedFunctionalityInAndroid("presentCodeRedemptionSheet")
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setAttributes(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val attributes = call.getObject("attributes")?.convertToMap() ?: emptyMap()
        setAttributesCommon(attributes)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setEmail(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val email = call.getString("email")
        setEmailCommon(email)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setPhoneNumber(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val phoneNumber = call.getString("phoneNumber")
        setPhoneNumberCommon(phoneNumber)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setDisplayName(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val displayName = call.getString("displayName")
        setDisplayNameCommon(displayName)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setPushToken(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val pushToken = call.getString("pushToken")
        setPushTokenCommon(pushToken)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setProxyURL(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val urlString = call.getString("url")
        setProxyURLStringCommon(urlString)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun collectDeviceIdentifiers(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        collectDeviceIdentifiersCommon()
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setAdjustID(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val adjustID = call.getString("adjustID")
        setAdjustIDCommon(adjustID)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setAppsflyerID(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val appsflyerID = call.getString("appsflyerID")
        setAppsflyerIDCommon(appsflyerID)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setFBAnonymousID(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val fbAnonymousID = call.getString("fbAnonymousID")
        setFBAnonymousIDCommon(fbAnonymousID)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setMparticleID(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val mparticleID = call.getString("mparticleID")
        setMparticleIDCommon(mparticleID)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setCleverTapID(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val cleverTapID = call.getString("cleverTapID")
        setCleverTapIDCommon(cleverTapID)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setMixpanelDistinctID(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val mixpanelDistinctID = call.getString("mixpanelDistinctID")
        setMixpanelDistinctIDCommon(mixpanelDistinctID)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setFirebaseAppInstanceID(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val firebaseAppInstanceID = call.getString("firebaseAppInstanceID")
        setFirebaseAppInstanceIDCommon(firebaseAppInstanceID)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setOnesignalID(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val onesignalID = call.getString("onesignalID")
        setOnesignalIDCommon(onesignalID)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setAirshipChannelID(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val airshipChannelID = call.getString("airshipChannelID")
        setAirshipChannelIDCommon(airshipChannelID)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setMediaSource(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val mediaSource = call.getString("mediaSource")
        setMediaSourceCommon(mediaSource)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setCampaign(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val campaign = call.getString("campaign")
        setCampaignCommon(campaign)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setAdGroup(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val adGroup = call.getString("adGroup")
        setAdGroupCommon(adGroup)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setAd(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val ad = call.getString("ad")
        setAdCommon(ad)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setKeyword(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val keyword = call.getString("keyword")
        setKeywordCommon(keyword)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun setCreative(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val creative = call.getString("creative")
        setCreativeCommon(creative)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun canMakePayments(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val features = call.getArray("features")?.toList<Int>() ?: emptyList()
        canMakePaymentsCommon(
            activity,
            features,
            object : OnResultAny<Boolean> {
                override fun onError(errorContainer: ErrorContainer?) {
                    if (errorContainer == null) call.reject("Unknown error calculating if payments can be performed")
                    else rejectWithErrorContainer(call, errorContainer)
                }

                override fun onReceived(result: Boolean) {
                    call.resolveWithMap(mapOf("canMakePayments" to result))
                }
            }
        )
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun beginRefundRequestForActiveEntitlement(call: PluginCall) {
        rejectNotSupportedInAndroid(call, "beginRefundRequestForActiveEntitlement")
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun beginRefundRequestForEntitlement(call: PluginCall) {
        rejectNotSupportedInAndroid(call, "beginRefundRequestForEntitlement")
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun beginRefundRequestForProduct(call: PluginCall) {
        rejectNotSupportedInAndroid(call, "beginRefundRequestForProduct")
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    fun showInAppMessages(call: PluginCall) {
        if (rejectIfNotConfigured(call)) return
        val messageTypes = call.getArray("messageTypes")?.toList<Int>()?.mapNotNull {
            InAppMessageType.values().getOrNull(it)
        }
        showInAppMessagesIfNeeded(activity, messageTypes)
        call.resolve()
    }

    @PluginMethod(returnType = PluginMethod.RETURN_PROMISE)
    fun isConfigured(call: PluginCall) {
        call.resolveWithMap(mapOf("isConfigured" to Purchases.isConfigured))
    }

    //================================================================================
    // Private methods
    //================================================================================

    private val activity: Activity
        get() = bridge.activity

    private fun getOnResult(call: PluginCall, wrapperKey: String? = null): OnResult {
        return object : OnResult {
            override fun onReceived(map: Map<String, *>) {
                val mapToConvert = wrapperKey?.let { mapOf(wrapperKey to map) } ?: map
                call.resolve(convertMapToJSObject(mapToConvert))
            }

            override fun onError(errorContainer: ErrorContainer) {
                rejectWithErrorContainer(call, errorContainer)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertMapToJSObject(readableMap: Map<String, Any?>): JSObject {
        val jsObject = JSObject()
        for ((key, value) in readableMap) {
            when (value) {
                null -> jsObject.put(key, JSObject.NULL)
                is Map<*, *> -> jsObject.put(key, convertMapToJSObject(value as Map<String, Any?>))
                is List<*> -> jsObject.put(key, convertListToJSArray(value))
                else -> jsObject.put(key, value)
            }
        }
        return jsObject
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertListToJSArray(array: List<*>): JSArray {
        val writableArray = JSArray()
        for (item in array) {
            when (item) {
                null -> writableArray.put(JSObject.NULL)
                is Map<*, *> -> writableArray.put(convertMapToJSObject(item as Map<String, *>))
                is List<*> -> writableArray.put(convertListToJSArray(item))
                else -> writableArray.put(item)
            }
        }
        return writableArray
    }

    private fun logNotSupportedFunctionalityInAndroid(functionName: String) {
        Log.e(
            "PurchasesCapacitor",
            "Functionality not supported in Android. Function: $functionName"
        )
    }

    private fun rejectIfNotConfigured(call: PluginCall): Boolean {
        val isConfigured = Purchases.isConfigured
        if (!isConfigured) {
            call.reject("Purchases must be configured before calling this function")
        }
        return !isConfigured
    }

    private fun rejectWithErrorContainer(call: PluginCall, errorContainer: ErrorContainer) {
        call.reject(
            errorContainer.message,
            errorContainer.code.toString(),
            convertMapToJSObject(errorContainer.info)
        )
    }

    private fun rejectNotSupportedInAndroid(call: PluginCall, functionName: String) {
        logNotSupportedFunctionalityInAndroid(functionName)
        call.reject("This operation is not supported in Android",
            "NOT_SUPPORTED",
            UnsupportedOperationException(),
        )
    }

    private fun PluginCall.resolveWithMap(map: Map<String, *>) {
        resolve(convertMapToJSObject(map))
    }

    private fun PluginCall.getStringOrReject(key: String): String? {
        val value = getString(key)
        if (value == null) {
            reject("Missing $key parameter")
            return null
        }
        return value
    }

    private fun PluginCall.getObjectOrReject(key: String): JSObject? {
        val value = getObject(key)
        if (value == null) {
            reject("Missing $key parameter")
            return null
        }
        return value
    }

    private fun PluginCall.getArrayOrReject(key: String): JSArray? {
        val value = getArray(key)
        if (value == null) {
            reject("Missing $key parameter")
            return null
        }
        return value
    }

    private fun PluginCall.getBooleanOrReject(key: String): Boolean? {
        val value = getBoolean(key)
        if (value == null) {
            reject("Missing $key parameter")
            return null
        }
        return value
    }

    private fun JSObject.getStringOrReject(call: PluginCall, key: String): String? {
        val value = getString(key)
        if (value == null) {
            call.reject("Missing $key parameter in $this")
            return null
        }
        return value
    }

    private data class PurchaseOptionalInfoParams(
        val oldProductIdentifier: String?,
        val prorationMode: Int?,
        val isPersonalizedPrice: Boolean?,
    ) {
        companion object {
            fun fromCall(call: PluginCall): PurchaseOptionalInfoParams {
                val googleProductChangeInfo = call.getObject("googleProductChangeInfo")
                val googleIsPersonalizedPrice = call.getBoolean("googleIsPersonalizedPrice")
                return PurchaseOptionalInfoParams(
                    oldProductIdentifier = googleProductChangeInfo?.getString("oldProductIdentifier"),
                    prorationMode = googleProductChangeInfo?.getInteger("prorationMode"),
                    isPersonalizedPrice = googleIsPersonalizedPrice,
                )
            }
        }
    }
}

package com.getcapacitor.community.stripe.paymentsheet;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.core.util.Supplier;

import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.getcapacitor.community.stripe.models.Executor;
import com.getcapacitor.community.stripe.paymentflow.PaymentFlowEvents;
import com.google.android.gms.common.util.BiConsumer;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.Stripe;
import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;
import com.stripe.android.paymentsheet.PaymentSheetResultCallback;

public class PaymentSheetExecutor extends Executor {

    public PaymentSheet paymentSheet;
    private final JSObject emptyObject = new JSObject();
    private PaymentSheet.Configuration paymentConfiguration;

    private String paymentIntentClientSecret;

    public PaymentSheetExecutor(
            Supplier<Context> contextSupplier,
            Supplier<Activity> activitySupplier,
            BiConsumer<String, JSObject> notifyListenersFunction,
            String pluginLogTag
    ) {
        super(contextSupplier, activitySupplier, notifyListenersFunction, pluginLogTag, "GooglePayExecutor");
        this.contextSupplier = contextSupplier;
    }

    public void createPaymentSheet(final PluginCall call) {
        paymentIntentClientSecret = call.getString("paymentIntentClientSecret", null);
        String customerEphemeralKeySecret = call.getString("customerEphemeralKeySecret", null);
        String customerId = call.getString("customerId", null);

        if (paymentIntentClientSecret == null) {
            notifyListenersFunction.accept(PaymentSheetEvents.FailedToLoad.getWebEventName(), emptyObject);
            call.reject("Invalid Params. this method require paymentIntentClientSecret.");
            return;
        }

        if (customerId != null && customerEphemeralKeySecret == null) {
            notifyListenersFunction.accept(PaymentFlowEvents.FailedToLoad.getWebEventName(), emptyObject);
            call.reject("Invalid Params. When you set customerId, you must set customerEphemeralKeySecret.");
            return;
        }

        String merchantDisplayName = call.getString("merchantDisplayName");

        if (merchantDisplayName == null) {
            merchantDisplayName = "";
        }

        Boolean enableGooglePay = call.getBoolean("enableGooglePay", false);

        final PaymentSheet.CustomerConfiguration customer = customerId != null
                ? new PaymentSheet.CustomerConfiguration(customerId, customerEphemeralKeySecret)
                : null;

        String countryCode = call.getString("countryCode", "US");
        PaymentSheet.BillingDetails defaultBillingDetails = new PaymentSheet.BillingDetails.Builder()
                .address(new PaymentSheet.Address.Builder()
                        .country(countryCode))
                .build();

        if (!enableGooglePay) {
            paymentConfiguration = new PaymentSheet.Configuration.Builder(merchantDisplayName)
                    .customer(customer)
                    .defaultBillingDetails(defaultBillingDetails)
                    .build();
        } else {
            Boolean GooglePayEnvironment = call.getBoolean("GooglePayIsTesting", false);

            PaymentSheet.GooglePayConfiguration.Environment environment = PaymentSheet.GooglePayConfiguration.Environment.Production;

            if (GooglePayEnvironment) {
                environment = PaymentSheet.GooglePayConfiguration.Environment.Test;
            }

            paymentConfiguration =
                    new PaymentSheet.Configuration.Builder(merchantDisplayName)
                            .customer(customer)
                            .defaultBillingDetails(defaultBillingDetails)
                            .googlePay(new PaymentSheet.GooglePayConfiguration(environment, countryCode)).build();
        }

        notifyListenersFunction.accept(PaymentSheetEvents.Loaded.getWebEventName(), emptyObject);
        call.resolve();
    }

    public void presentPaymentSheet(final PluginCall call) {
        try {
            paymentSheet.presentWithPaymentIntent(paymentIntentClientSecret, paymentConfiguration);
        } catch (Exception ex) {
            call.reject(ex.getLocalizedMessage(), ex);
        }
    }

    public void onPaymentSheetResult(Bridge bridge, String callbackId, final PaymentSheetResult paymentSheetResult) {
        PluginCall call = bridge.getSavedCall(callbackId);

        if (paymentSheetResult instanceof PaymentSheetResult.Canceled) {
            notifyListenersFunction.accept(PaymentSheetEvents.Canceled.getWebEventName(), emptyObject);
            call.resolve(new JSObject().put("paymentResult", PaymentSheetEvents.Canceled.getWebEventName()));
        } else if (paymentSheetResult instanceof PaymentSheetResult.Failed) {
            notifyListenersFunction.accept(
                    PaymentSheetEvents.Failed.getWebEventName(),
                    new JSObject().put("error", ((PaymentSheetResult.Failed) paymentSheetResult).getError().getLocalizedMessage())
            );
            notifyListenersFunction.accept(PaymentSheetEvents.Failed.getWebEventName(), emptyObject);
            call.resolve(new JSObject().put("paymentResult", PaymentSheetEvents.Failed.getWebEventName()));
        } else if (paymentSheetResult instanceof PaymentSheetResult.Completed) {
            notifyListenersFunction.accept(PaymentSheetEvents.Completed.getWebEventName(), emptyObject);
            call.resolve(new JSObject().put("paymentResult", PaymentSheetEvents.Completed.getWebEventName()));
        }
    }
}

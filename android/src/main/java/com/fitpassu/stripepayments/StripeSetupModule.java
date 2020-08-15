package com.fitpassu.stripepayments;

import android.app.Activity;
import android.content.Intent;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.BaseActivityEventListener;

import com.facebook.react.bridge.WritableMap;
import com.stripe.android.ApiResultCallback;
import com.stripe.android.PaymentConfiguration;
// import com.stripe.android.PaymentIntentResult;
import com.stripe.android.Stripe;
import com.stripe.android.model.Card;
// import com.stripe.android.model.ConfirmPaymentIntentParams;
// import com.stripe.android.model.PaymentIntent;
import com.stripe.android.model.PaymentMethodCreateParams;
import com.stripe.android.model.SetupIntent;
import com.stripe.android.SetupIntentResult;
import com.stripe.android.model.ConfirmSetupIntentParams;
import com.stripe.android.model.PaymentMethod;
import com.stripe.android.model.PaymentMethod.BillingDetails;



public class StripeSetupModule extends ReactContextBaseJavaModule {

    private static ReactApplicationContext reactContext;

    private Stripe stripe;
    private Promise setupPromise;
    
    private final ActivityEventListener activityListener = new BaseActivityEventListener() {

        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
            if (setupPromise == null || stripe == null) {
                super.onActivityResult(activity, requestCode, resultCode, data);
                return;
            }
            boolean handled = stripe.onSetupResult(requestCode, data, new SetupResultCallback(setupPromise));
            if (!handled) {
                super.onActivityResult(activity, requestCode, resultCode, data);
            }
        }
    };

    StripeSetupModule(ReactApplicationContext context) {
        super(context);

        context.addActivityEventListener(activityListener);

        reactContext = context;
    }

    @Override
    public String getName() {
        return "StripeSetupModule";
    }

    // @ReactMethod(isBlockingSynchronousMethod = true)
    // public void init(String publishableKey) {
    //     SetupConfiguration.init(
    //             reactContext,
    //             publishableKey
    //     );
    // }

    // @ReactMethod(isBlockingSynchronousMethod =  true)
    // public boolean isCardValid(ReadableMap cardParams) {
    //     Card card =  new Card.Builder(
    //                 cardParams.getString("number"),
    //                 cardParams.getInt("expMonth"),
    //                 cardParams.getInt("expYear"),
    //                 cardParams.getString("cvc")
    //             )
    //             .build();
    //     return card.validateNumber() && card.validateExpiryDate() && card.validateExpMonth() && card.validateCVC();
    // }

    
    @ReactMethod
    public void setupCard(String secret, ReadableMap cardParams, final Promise promise) {
        PaymentMethodCreateParams.Card card = new PaymentMethodCreateParams.Card(
                cardParams.getString("number"),
                cardParams.getInt("expMonth"),
                cardParams.getInt("expYear"),
                cardParams.getString("cvc"),
                null,
                null
        );
        PaymentMethod.BillingDetails billingDetails = (new PaymentMethod.BillingDetails.Builder()).setEmail(cardParams.getString("email")).build();
        // PaymentMethodCreateParams params = PaymentMethodCreateParams.create(card);
        PaymentMethodCreateParams params = PaymentMethodCreateParams.create(card, billingDetails);

        ConfirmSetupIntentParams confirmParams = ConfirmSetupIntentParams.create(params, secret);


        if (params == null) {
            promise.reject("", "StripeModule.invalidSetupIntentParams");
            return;
        }

        setupPromise = promise;
        stripe = new Stripe(
                reactContext,
                PaymentConfiguration.getInstance(reactContext).getPublishableKey()
        );
        stripe.confirmSetupIntent(getCurrentActivity(), confirmParams);

    }
    private static final class SetupResultCallback implements ApiResultCallback<SetupIntentResult> {
        private final Promise promise;

        SetupResultCallback(Promise promise) {
            this.promise = promise;
        }

        @Override
        public void onSuccess(SetupIntentResult result) {
            SetupIntent setupIntent = result.getIntent();
            SetupIntent.Status status = setupIntent.getStatus();

            if (
                    status == SetupIntent.Status.Succeeded ||
                    status == SetupIntent.Status.Processing
            ) {
                WritableMap map = Arguments.createMap();
                map.putString("id", setupIntent.getId());
                map.putString("paymentMethodId", setupIntent.getPaymentMethodId());
                promise.resolve(map);
            } else if (status == SetupIntent.Status.Canceled) {
                promise.reject("StripeModule.cancelled", "");
            } else {
                promise.reject("StripeModule.failed", status.toString());
            }
        }
        @Override
        public void onError(Exception e) {
            promise.reject("StripeModule.failed", e.toString());
        }
    }}
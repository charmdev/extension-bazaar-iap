package org.haxe.extension.iap;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import org.haxe.extension.iap.util.*;
import org.haxe.extension.Extension;
import org.haxe.lime.HaxeObject;

import org.json.JSONException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLSurfaceView;

public class InAppPurchase extends Extension {
	
	private static HaxeObject callback = null;
	private static IabHelper inAppPurchaseHelper;
	private static String publicKey = "";

	public static void buy (final String productID, final String devPayload) {
		// IabHelper.launchPurchaseFlow() must be called from the main activity's UI thread
		Extension.mainActivity.runOnUiThread(new Runnable() {
				public void run() {
					try {
						InAppPurchase.inAppPurchaseHelper.launchPurchaseFlow (Extension.mainActivity, productID, 1001, mPurchaseFinishedListener, devPayload);
					} catch (Exception exception) {
						// see: https://github.com/openfl/extension-iap/issues/28
						Log.e("Failed to launch purchase flow." + exception.getMessage());
						mPurchaseFinishedListener.onIabPurchaseFinished(
							new IabResult(IabHelper.BILLING_RESPONSE_RESULT_ERROR, null),
							null);
					}
				}
			});
	}
	
	public static void consume (final String purchaseJson, final String signature) 
	{
		Extension.callbackHandler.post (new Runnable () 
		{
			@Override public void run () 
			{
				try {
					final Purchase purchase = new Purchase("inapp", purchaseJson, signature);
					InAppPurchase.inAppPurchaseHelper.consumeAsync(purchase, mConsumeFinishedListener);
				} 
				catch (JSONException e) 
				{
					// This is not a normal consume failure, just a Json parsing error
					String resultJson = "{\"response\": -999, \"message\":\"Json Parse Error \"}";
					fireCallback("onFailedConsume", new Object[] { ("{\"result\":" + resultJson + ", \"product\":" + null  + "}") });
				}
			}
		});
	}

	public static void querySkuDetails(final String[] ids) {
		final List<String> moreSkus = Arrays.asList(ids);
		Extension.mainActivity.runOnUiThread(new Runnable() {
			public void run() {
				try {
					InAppPurchase.inAppPurchaseHelper.querySkuDetailsAsync(moreSkus, mGotSkuDetailsListener);
				} catch(Exception e) {
					Log.d(e.getMessage());
				}
			}
		});

	}
	
	public static void queryInventory () {
		final List<String> moreSkus = new ArrayList();; 
		Extension.mainActivity.runOnUiThread(new Runnable() {
			public void run() {
				try {
					InAppPurchase.inAppPurchaseHelper.queryInventoryAsync(false, moreSkus, mGotInventoryListener);
				} catch(Exception e) {
					Log.d(e.getMessage());
				}
			}
		});
	}
	
	public static String getPublicKey () {
		return publicKey;
	}
	
	
	public static void initialize (String publicKey, HaxeObject callback) {
		Log.initialize(callback);
		Log.i ("Initializing billing service");
		
		InAppPurchase.publicKey = publicKey;
		InAppPurchase.callback = callback;
		
		if (InAppPurchase.inAppPurchaseHelper != null) {
			InAppPurchase.inAppPurchaseHelper.dispose ();
		}
		
		InAppPurchase.inAppPurchaseHelper = new IabHelper (Extension.mainContext, publicKey);
		//InAppPurchase.inAppPurchaseHelper.enableDebugLogging(true, "Billing hx");
		InAppPurchase.inAppPurchaseHelper.startSetup (new IabHelper.OnIabSetupFinishedListener () {
			
			public void onIabSetupFinished (final IabResult result) {
				
				if (result.isSuccess ()) {
					Extension.callbackHandler.post (new Runnable () {
						@Override public void run () {
							Log.i ("Query Inventory");
							queryInventory();
						}
					});
					
				} else {
					Extension.callbackHandler.post (new Runnable () {
						@Override public void run () {
							InAppPurchase.callback.call ("onStarted", new Object[] { "Failure" });
						}
					});
				}
			}
		});
	}
	
	@Override public boolean onActivityResult (int requestCode, int resultCode, Intent data) {
		if (inAppPurchaseHelper != null) {
			return !inAppPurchaseHelper.handleActivityResult (requestCode, resultCode, data);
		}

		return super.onActivityResult (requestCode, resultCode, data);
	}

	@Override public void onCreate (Bundle savedInstanceState) {
		if (InAppPurchase.inAppPurchaseHelper != null) {
			//InAppPurchase.inAppPurchaseHelper.onReCreate(Extension.mainActivity);
		}
	}

	@Override public void onDestroy () {
		if (InAppPurchase.inAppPurchaseHelper != null) {
			InAppPurchase.inAppPurchaseHelper.dispose ();
			InAppPurchase.inAppPurchaseHelper = null;
		}
	}
	
	
	public static void setPublicKey (String s) {
		publicKey = s;
	}

	private static void fireCallback(final String name, final Object[] payload)
	{
		if (Extension.mainView == null) return;
		GLSurfaceView view = (GLSurfaceView) Extension.mainView;

		view.queueEvent(new Runnable()
		{
			public void run()
			{
				if (InAppPurchase.callback != null)
				{
					InAppPurchase.callback.call(name, payload);
				}
			}
		});
	}
	
	
	static IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
	   
		public void onQueryInventoryFinished(final IabResult result, final Inventory inventory) {
			Log.i ("onQueryInventoryFinished");
			Log.i (Boolean.toString(result.isFailure()));

		  if (result.isFailure()) {
			Log.i("onQueryInventoryFailure" + result.toString());
			fireCallback("onStarted", new Object[] { "Failure" });
		  }
		  else {
			String serialized = inventory.toJsonString();
			Log.i ("Serialized inventory" + serialized);
			fireCallback("onQueryInventoryComplete", new Object[] {serialized});
			fireCallback("onStarted", new Object[] { "Success" });
		  }
	   }
	   
	};

	static IabHelper.QuerySkuDetailsFinishedListener mGotSkuDetailsListener =  new IabHelper.QuerySkuDetailsFinishedListener() {
		public void onQuerySkuDetailsFinished(final IabResult result, final Inventory inventory) {
			Log.i ("onQuerySkuDetailsFinished");
			Log.i (Boolean.toString(result.isFailure()));

		  if (result.isFailure()) {
			fireCallback("onQuerySkuDetailsComplete", new Object[] { "Failure" });
		  }
		  else {
			  //Log.i ("IAP", inventory.toJsonString());
			fireCallback("onQuerySkuDetailsComplete", new Object[] {inventory.toJsonString()});
		  }
	   }
	};
	
	
	static IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener () {
		public void onIabPurchaseFinished (final IabResult result, final Purchase purchase)
		{
			if (result.isFailure ()) 
			{
				if (result.getResponse() == IabHelper.IABHELPER_USER_CANCELLED) {
					fireCallback("onCanceledPurchase", new Object[] { ((purchase == null) ? "null" : purchase.getPackageName()) });
				} else {
					fireCallback("onFailedPurchase", new Object[] { ("{\"result\":" + result.toJsonString() + ", \"product\":" + ((purchase != null)? purchase.getOriginalJson() : "null") + "}") });
				}
			} 
			else
			{
				Log.d("got purchase response: " + purchase.getOriginalJson());
				fireCallback("onPurchase", new Object[] { purchase.getOriginalJson(), purchase.getItemType(), purchase.getSignature() });
			}
		}
	};
	
	
	static IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener () {
		
		public void onConsumeFinished (final Purchase purchase, final IabResult result) {
			if (result.isFailure ()) 
			{
				fireCallback("onFailedConsume", new Object[] { ("{\"result\":" + result.toJsonString() + ", \"product\":" + purchase.getOriginalJson() + "}") });
			} 
			else
			{
				fireCallback("onConsume", new Object[] { purchase.getOriginalJson() });
			}
		}
	};
}

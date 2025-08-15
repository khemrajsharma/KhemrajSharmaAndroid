package mgks.os.swv;

/*
  Smart WebView v7
  https://github.com/mgks/Android-SmartWebView

  A modern, open-source WebView wrapper for building advanced hybrid Android apps.
  Native features, modular plugins, and full customisation—built for developers.

  - Documentation: https://docs.mgks.dev/smart-webview  
  - Plugins: https://docs.mgks.dev/smart-webview/plugins  
  - Discussions: https://github.com/mgks/Android-SmartWebView/discussions  
  - Sponsor the Project: https://github.com/sponsors/mgks  

  MIT License — https://opensource.org/licenses/MIT  

  Mentioning Smart WebView in your project helps others find it and keeps the dev loop alive.
*/

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.provider.Settings;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessaging;

import in.mobtown.one.R;

import java.lang.reflect.Field;
import java.math.BigInteger;

import java.security.SecureRandom;

import java.util.regex.Pattern;

public class Functions{
	private final SecureRandom random = new SecureRandom();

	// Random ID creation function to help get fresh cache every-time webview reloaded
	public String random_id() {
		return new BigInteger(130, random).toString(32);
	}

	// Printing the page in view
	static void print_page(WebView view, String print_name, Activity activityContext){
		// Create a PrintDocumentAdapter
		PrintDocumentAdapter printAdapter = view.createPrintDocumentAdapter(print_name);

		// Create PrintAttributes (optional)
		PrintAttributes.Builder builder = new PrintAttributes.Builder();
		builder.setMediaSize(PrintAttributes.MediaSize.ISO_A5); // Customize as needed

		// Get the PrintManager service using the activity context
		PrintManager printManager = (PrintManager) activityContext.getSystemService(Context.PRINT_SERVICE);

		// Start the print job
		if (printManager != null) {
			PrintJob printJob = printManager.print(print_name, printAdapter, builder.build());

			// Monitor print job status (optional)
			if (printJob.isCompleted()) {
				Toast.makeText(activityContext, R.string.print_complete, Toast.LENGTH_LONG).show();
			} else if (printJob.isFailed()) {
				Toast.makeText(activityContext, R.string.print_failed, Toast.LENGTH_LONG).show();
			}
		} else {
			Toast.makeText(activityContext, R.string.print_error, Toast.LENGTH_LONG).show();
		}
	}

	// Checking if internet/network is available
	public static boolean isInternetAvailable(Context context) {
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivityManager == null) {
			Log.e("NetworkUtils", "ConnectivityManager is null");
			return false; // Handling the absence of ConnectivityManager as needed
		}
		Network network = connectivityManager.getActiveNetwork();
		if (network == null) {
			return false;
		}
		NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
		return capabilities != null &&
				(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
						capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
						capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
						capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
	}

	// Opening URLs inside webview with request
	void aswm_view(String url, Boolean tab, int error_counter, Activity activity) {
		if (error_counter > 2) {
			exit_app(activity);
		} else {
			if (tab) {
				if (SWVContext.ASWP_TAB) {
					CustomTabsIntent.Builder intentBuilder = new CustomTabsIntent.Builder();
					intentBuilder.setStartAnimations(activity, android.R.anim.slide_in_left, android.R.anim.slide_out_right);
					intentBuilder.setExitAnimations(activity, android.R.anim.slide_in_left, android.R.anim.slide_out_right);
					CustomTabsIntent customTabsIntent = intentBuilder.build();
					try {
						customTabsIntent.launchUrl(activity, Uri.parse(url));
					} catch (ActivityNotFoundException e) {
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setData(Uri.parse(url));
						activity.startActivity(intent);
					}
				} else {
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setData(Uri.parse(url));
					activity.startActivity(intent);
				}
			} else {
				// Check to see whether the url already has query parameters and handle appropriately
				if (!url.startsWith("file://")) {
					url = url + (url.contains("?") ? "&" : "?") + "rid=" + random_id();
				}
				SWVContext.asw_view.loadUrl(url);
			}
		}
	}

	// Push JavaScript into webview
	public static void push_js(WebView view, String class_name, String html) {
		view.evaluateJavascript(
				"document.getElementsByClassName('" + class_name + "')[0].innerHTML = `" + html + "`;", null);
	}

	// Get data from webview DOM field
	public Object swv_get(String fieldName) throws NoSuchFieldException, IllegalAccessException {
		Field field = getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(this);
	}

	// Set data to webview DOM field
	public boolean swv_set(String fieldName, Object value) {
		try {
			Field field = getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(this, value);
			return true;
		} catch (NoSuchFieldException | IllegalAccessException e) {
			Log.e("ERROR", String.valueOf(e));
			return false;
		}
	}

	// URL actions based on URL structure
	public boolean url_actions(WebView view, String url, Activity activity) {
		boolean a = true;
		Context context = activity.getApplicationContext();

		// Show toast error if not connected to the network
		if (!SWVContext.ASWP_OFFLINE && !isInternetAvailable(context)) {
			Toast.makeText(context, context.getString(R.string.check_connection), Toast.LENGTH_SHORT).show();
			return true; // We've handled it by showing a toast
		}

		// Handle specific, known custom schemes first
		// Redirect back to default URL :: refresh:android
		if (url.startsWith("refresh:")) {
			String ref_sch = (Uri.parse(url).toString()).replace("refresh:", "");
			if (ref_sch.matches("URL")) {
				SWVContext.CURR_URL = SWVContext.ASWV_URL;
			}
			pull_fresh(activity);
			return true;

		} else if (url.startsWith("tel:")) {
			try {
				Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
				activity.startActivity(intent);
			} catch (ActivityNotFoundException e) {
				Toast.makeText(context, "No dialer app found.", Toast.LENGTH_SHORT).show();
			}
			return true;

		} else if (url.startsWith("rate:")) {
			final String app_package = context.getPackageName();
			try {
				activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + app_package)));
			} catch (ActivityNotFoundException anfe) {
				activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + app_package)));
			}
			return true;

		} else if (url.startsWith("share:")) {
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_SUBJECT, view.getTitle());
			intent.putExtra(Intent.EXTRA_TEXT, view.getTitle() + " Visit: " + (Uri.parse(url).toString()).replace("share:", ""));
			activity.startActivity(Intent.createChooser(intent, context.getString(R.string.share_w_friends)));
			return true;

		} else if (url.startsWith("exit:")) {
			exit_app(activity);
			return true;

		} else if (url.startsWith("fcm:")) {
			String title = null, body = null, nuri = null;

			// Manually parse parameters from fcm: URL
			String[] parts = url.substring(4).split("&"); // Remove "fcm:" and split by &
			for (String part : parts) {
				String[] keyValue = part.split("=");
				if (keyValue.length == 2) {
					String key = keyValue[0];
					String value = keyValue[1];
					switch (key) {
						case "title":
							title = value;
							break;
						case "body":
							body = value;
							break;
						case "uri":
							nuri = value;
							break;
					}
				}
			}

			// Set default values if not found
			if (title == null || title.isEmpty()) {
				title = "Hello Developer!";
			}
			if (body == null || body.isEmpty()) {
				body = "This is a test notification from Smart WebView.";
			}
			if (nuri == null || nuri.isEmpty()) {
				nuri = SWVContext.ASWV_URL;
			}

			PermissionManager permissionManager = new PermissionManager(activity);
			if(permissionManager.isNotificationPermissionGranted()) {
				// Send the notification
				Firebase firebase = new Firebase();
				firebase.sendMyNotification(title, body, "OPEN_URI", nuri, null, String.valueOf(SWVContext.ASWV_FCM_ID), context);
			}else{
				// Request the permission
				permissionManager.requestInitialPermissions(); // Or a dedicated notification request
				Toast.makeText(context, "Please grant notification permission and try again.", Toast.LENGTH_SHORT).show();
			}
			return true;

		} else if (url.startsWith("print:")) {
			print_page(view, view.getTitle(), activity);
			return true;
		}

		// Handle standard web protocols
		if (url.startsWith("http:") || url.startsWith("https:")) {
			// Check if it's an external URL that should be opened outside
			if (SWVContext.ASWP_EXTURL && !aswm_host(url).equals(SWVContext.ASWV_HOST) && !SWVContext.ASWV_EXC_LIST.contains(aswm_host(url))) {
				aswm_view(url, true, SWVContext.asw_error_counter, activity);
				return true; // We've handled it by opening externally
			}
			// It's an internal link, so let the WebView load it
			return false;
		}
		// --- THIS IS THE CATCH-ALL FOR ANY OTHER UNKNOWN SCHEME ---
		// Includes mailto:, geo:, sms:, and any discontinued protocol
		try {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			activity.startActivity(intent);
			return true; // We've attempted to handle it

		} catch (ActivityNotFoundException e) {
			// CRASH PREVENTION: The system could not find an app to handle this URL scheme.
			Log.w("URL_SCHEME_ERROR", "Could not handle unknown URL scheme: " + url, e);
			if (SWVContext.SWV_DEBUGMODE) {
				Toast.makeText(context, "Unhandled URL scheme: " + url, Toast.LENGTH_SHORT).show();
			}
			return true; // Return true because we've "handled" it by catching the error.
		}
	}

	// Getting host name
	public static String aswm_host(String url) {
		if (url == null || url.isEmpty()) {
			return "";
		}
		int dslash = url.indexOf("//");
		if (dslash == -1) {
			dslash = 0;
		} else {
			dslash += 2;
		}
		int end = url.indexOf('/', dslash);
		end = end >= 0 ? end : url.length();
		int port = url.indexOf(':', dslash);
		end = (port > 0 && port < end) ? port : end;
		Log.i("SLOG_URL_HOST", url.substring(dslash, end));
		return url.substring(dslash, end);
	}

	// Reloading current page
	public void pull_fresh(Activity activity) {
		String currentUrl = SWVContext.asw_view.getUrl();
		// Use the current webview URL, fallback to the configured URL if it's null/empty
		String urlToReload = (currentUrl != null && !currentUrl.isEmpty()) ? currentUrl : SWVContext.ASWV_URL;
		aswm_view(urlToReload, false, 0, activity); // Reset error counter on manual refresh
	}

	// Changing port view
	@SuppressLint("SourceLockedOrientationActivity")
	public void set_orientation(int orientation, boolean cookie, Context context) { // setting the view port var
		if (context instanceof Activity activity) {
			if (orientation == 1) {
				activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			} else if (orientation == 2) {
				activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			} else if (orientation == 5) { //experimental switch
				SWVContext.ASWV_ORIENTATION = (SWVContext.ASWV_ORIENTATION == 1 ? 2 : 1);
			} else {
				activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
			}
			if (cookie) {
				set_cookie("ORIENT=" + orientation);
			}
		}
	}

	// Setting cookies
	public void set_cookie(String data) {
		if(SWVContext.true_online) {
			// Cookie manager initialisation
			SWVContext.cookie_manager = CookieManager.getInstance();
			SWVContext.cookie_manager.setAcceptCookie(true);
			SWVContext.cookie_manager.setCookie(SWVContext.ASWV_URL, data);
			if(SWVContext.SWV_DEBUGMODE) {
				Log.d("SLOG_COOKIES", SWVContext.cookie_manager.getCookie(SWVContext.ASWV_URL));
			}
		}
	}

	// Getting device basic information
	public String[] get_info(Context context) { // Add context parameter
		String[] info = new String[3];
		info[0] = "android";
		info[1] = new MetaPull(context).device(); // Pass context
		info[2] = new MetaPull(context).swv(); // Pass context

		// Set dark mode status
		SWVContext.ASWP_DARK_MODE = is_night_mode(context);

		set_cookie("DEVICE_TYPE=" + info[0]);
		set_cookie("DEVICE_INFO=" + info[1]);
		set_cookie("APP_INFO=" + info[2]);

		return info;
	}

	// Check if the device is in dark mode
	public static boolean is_night_mode(Context context) {
		int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
		return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
	}

	// Get cookie value
	public String get_cookies(String cookie) {
		String value = "";
		if(SWVContext.true_online) {
			SWVContext.cookie_manager = CookieManager.getInstance();
			String cookies = SWVContext.cookie_manager.getCookie(SWVContext.ASWV_URL);
			if (cookies !=null && !cookies.isEmpty()) {
				String[] temp = cookies.split(";");
				for (String ar1 : temp) {
					if (ar1.contains(cookie)) {
						String[] temp1 = ar1.split("=");
						value = temp1[1];
						break;
					}
				}
			}else{
				value = "";
				if(SWVContext.SWV_DEBUGMODE) {
					Log.d("SLOG_COOKIES", "Cookies either NULL or Empty");
				}
			}
		}else{
			Log.w("SLOG_NETWORK","DEVICE NOT ONLINE");
		}
		return value;
	}

	// Divide the URL pattern into pieces
	public static Pattern url_pattern() {
		return Pattern.compile("(?:^|\\W)((ht|f)tp(s?)://|www\\.)" + "(([\\w\\-]+\\.)+([\\w\\-.~]+/?)*" + "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]*$~@!:/{};']*)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
	}

	public interface TokenCallback {
		void onTokenReceived(String token);
		void onTokenFailed(Exception e);
	}

	// Get fresh firebase tokens
	public void fcm_token(final TokenCallback callback) {
		FirebaseMessaging.getInstance().getToken()
				.addOnSuccessListener(token -> {
					if (!SWVContext.ASWP_OFFLINE) {
						set_cookie("FCM_TOKEN=" + token);
						if (SWVContext.SWV_DEBUGMODE) {
							Log.d("SLOG_FCM_BAKED", "YES");
							Log.d("SLOG_COOKIES", get_cookies(SWVContext.ASWV_URL));
						}
					}
					SWVContext.fcm_token = token;
					if (SWVContext.SWV_DEBUGMODE) {
						Log.d("SLOG_REQ_FCM_TOKEN", token);
					}
					callback.onTokenReceived(token); // Pass token to callback
				})
				.addOnFailureListener(e -> {
					SWVContext.fcm_token = "";
					Log.e("SLOG_REQ_FCM_TOKEN", "FAILED", e);
					callback.onTokenFailed(e); // Pass exception to callback
				});
	}

	// Injecting Google Analytics (gtag.js)
	public void inject_gtag(WebView webView, String gaId) {
		String gtag_code = "function load_gtag(){var script = document.createElement('script');script.async = true;script.src = 'https://www.googletagmanager.com/gtag/js?id=" + gaId + "';var firstScript = document.getElementsByTagName('script')[0];firstScript.parentNode.insertBefore(script, firstScript);window.dataLayer = window.dataLayer || [];function gtag(){dataLayer.push(arguments);}gtag('js', new Date());gtag('config', '" + gaId + "');console.log('Google Analytics (gtag.js) loaded.');} load_gtag();";
		webView.evaluateJavascript(gtag_code, null);
	}

	// Creating custom notifications with IDs
	public void show_notification(int type, int id, Context context) {
		long when = System.currentTimeMillis();
		String cont_title = "", cont_text = "", cont_desc = "";

		SWVContext.asw_notification = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		Intent i = new Intent();
		if (type == 1) {
			i.setClass(context, MainActivity.class);
		} else if (type == 2) {
			i.setAction(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
		} else {
			i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
			i.addCategory(Intent.CATEGORY_DEFAULT);
			i.setData(Uri.parse("package:" + context.getPackageName()));
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
			i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		}
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

		PendingIntent pendingIntent;
		final int flag = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
		pendingIntent = PendingIntent.getActivity(context, 0, i, flag);
		Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "");
		builder.setTicker(context.getString(R.string.app_name));
		switch (type) {
			case 1:
				cont_title = context.getString(R.string.loc_fail);
				cont_text = context.getString(R.string.loc_fail_text);
				cont_desc = context.getString(R.string.loc_fail_more);
				break;

			case 2:
				cont_title = context.getString(R.string.loc_perm);
				cont_text = context.getString(R.string.loc_perm_text);
				cont_desc = context.getString(R.string.loc_perm_more);
				builder.setSound(alarmSound);
				break;
		}
		builder.setContentTitle(cont_title);
		builder.setContentText(cont_text);
		builder.setStyle(new NotificationCompat.BigTextStyle().bigText(cont_desc));
		builder.setVibrate(new long[]{350, 700, 350, 700, 350});
		builder.setSmallIcon(R.mipmap.ic_launcher);
		builder.setOngoing(false);
		builder.setAutoCancel(true);
		builder.setWhen(when);
		builder.setContentIntent(pendingIntent);
		SWVContext.asw_notification_new = builder.build();
		SWVContext.asw_notification.notify(id, SWVContext.asw_notification_new);
	}

	// Exit app
	public void exit_app(Activity activity) {
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_HOME);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		activity.startActivity(intent);
	}

	// Creating exit dialogue
	public void ask_exit(Activity activity) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(activity.getString(R.string.exit_title));
		builder.setMessage(activity.getString(R.string.exit_subtitle));
		builder.setCancelable(true);

		// Action if user selects 'yes'
		builder.setPositiveButton("Yes", (dialogInterface, i) -> exit_app(activity));

		// Actions if user selects 'no'
		builder.setNegativeButton("No", (dialogInterface, i) -> {});

		// Create the alert dialog using alert dialog builder
		AlertDialog dialog = builder.create();

		// Finally, display the dialog when user press back button
		dialog.show();
	}
}
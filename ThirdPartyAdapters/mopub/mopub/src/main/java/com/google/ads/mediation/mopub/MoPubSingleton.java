package com.google.ads.mediation.mopub;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.mediation.MediationAdConfiguration;
import com.mopub.common.MoPub;
import com.mopub.common.MoPubReward;
import com.mopub.common.SdkConfiguration;
import com.mopub.common.SdkInitializationListener;
import com.mopub.common.logging.MoPubLog;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubRewardedVideoListener;
import com.mopub.mobileads.MoPubRewardedVideoManager;
import com.mopub.mobileads.MoPubRewardedVideos;
import com.mopub.mobileads.dfp.adapters.MoPubAdapter;
import com.mopub.mobileads.dfp.adapters.MoPubAdapterRewardedListener;

import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.google.ads.mediation.mopub.MoPubMediationAdapter.ERROR_AD_ALREADY_LOADED;

public class MoPubSingleton implements MoPubRewardedVideoListener {

  private static MoPubSingleton instance;
  private static boolean isInitializing;

  private ArrayList<SdkInitializationListener> mInitListeners = new ArrayList<>();
  private static Map<String, MoPubAdapterRewardedListener> mListeners =
          new SoftHashMap<>();

  public static MoPubSingleton getInstance() {
    if (instance == null) {
      instance = new MoPubSingleton();
    }
    return instance;
  }

  void adExpired(String adUnitID, MoPubRewardedVideoListener listener) {
    // Verify if the passed MoPubRewardedVideoListener instance matches the registered instance for
    // the given MoPub Ad Unit ID before removing from the list of listeners.
    if (listener != null && listener.equals(mListeners.get(adUnitID))) {
      mListeners.remove(adUnitID);
    }
  }

  boolean showRewardedAd(@NonNull String adUnitID, @Nullable String customData) {
    if (!TextUtils.isEmpty(adUnitID) && MoPubRewardedVideos.hasRewardedVideo(adUnitID)) {
      Log.d(MoPubMediationAdapter.TAG, "Showing a MoPub rewarded video.");
      MoPubRewardedVideos.showRewardedVideo(adUnitID, customData);
      return true;
    } else {
      mListeners.remove(adUnitID);
      return false;
    }
  }

  public void initializeMoPubSDK(
      Context context, SdkConfiguration configuration, SdkInitializationListener listener) {
    if (MoPub.isSdkInitialized()) {
      MoPubRewardedVideos.setRewardedVideoListener(MoPubSingleton.this);
      listener.onInitializationFinished();
      return;
    }

    mInitListeners.add(listener);
    if (!isInitializing) {
      isInitializing = true;

      MoPub.initializeSdk(
          context,
          configuration,
          new SdkInitializationListener() {
            @Override
            public void onInitializationFinished() {
              MoPubLog.d("MoPub SDK initialized.");
              isInitializing = false;

              MoPubRewardedVideos.setRewardedVideoListener(MoPubSingleton.this);
              for (SdkInitializationListener initListener : mInitListeners) {
                initListener.onInitializationFinished();
              }
              mInitListeners.clear();
            }
          });
    }
  }

  public void loadRewardedAd(
      Context context,
      final String adUnitID,
      final MoPubRewardedVideoManager.RequestParameters requestParameters,
      final MoPubAdapterRewardedListener adapterRewardedListener) {
    if (mListeners.containsKey(adUnitID)) {
      String errorMessage =
          "An ad has already been requested for the MoPub Ad Unit ID: " + adUnitID;
      adapterRewardedListener.onAdFailedToLoad(ERROR_AD_ALREADY_LOADED, errorMessage);
      return;
    }

    mListeners.put(adUnitID, adapterRewardedListener);

    SdkConfiguration configuration = new SdkConfiguration.Builder(adUnitID).build();
    initializeMoPubSDK(
        context,
        configuration,
        new SdkInitializationListener() {
          @Override
          public void onInitializationFinished() {
            MoPubRewardedVideos.loadRewardedVideo(adUnitID, requestParameters);
          }
        });
  }

  static String getKeywords(
      MediationAdConfiguration mediationConfiguration, boolean intendedForPII) {
    if (intendedForPII) {
      if (MoPub.canCollectPersonalInformation()) {
        return containsPII(mediationConfiguration) ? MoPubAdapter.MOPUB_NATIVE_CEVENT_VERSION : "";
      } else {
        return "";
      }
    } else {
      return containsPII(mediationConfiguration) ? "" : MoPubAdapter.MOPUB_NATIVE_CEVENT_VERSION;
    }
  }

  static boolean containsPII(MediationAdConfiguration configuration) {
    return configuration.getLocation() != null;
  }

  /** {@link MoPubRewardedVideoListener} implementation */
  @Override
  public void onRewardedVideoLoadSuccess(@NonNull String adUnitId) {
    MoPubAdapterRewardedListener listener = mListeners.get(adUnitId);
    if (listener != null) {
      listener.onRewardedVideoLoadSuccess(adUnitId);
    }
  }

  @Override
  public void onRewardedVideoLoadFailure(
      @NonNull String adUnitId, @NonNull MoPubErrorCode errorCode) {
    MoPubAdapterRewardedListener listener = mListeners.get(adUnitId);
    if (listener != null) {
      listener.onRewardedVideoLoadFailure(adUnitId, errorCode);
    }
    mListeners.remove(adUnitId);
  }

  @Override
  public void onRewardedVideoStarted(@NonNull String adUnitId) {
    MoPubAdapterRewardedListener listener = mListeners.get(adUnitId);
    if (listener != null) {
      listener.onRewardedVideoStarted(adUnitId);
    }
  }

  @Override
  public void onRewardedVideoPlaybackError(
      @NonNull String adUnitId, @NonNull MoPubErrorCode errorCode) {
    MoPubAdapterRewardedListener listener = mListeners.get(adUnitId);
    if (listener != null) {
      listener.onRewardedVideoPlaybackError(adUnitId, errorCode);
    }
    mListeners.remove(adUnitId);
  }

  @Override
  public void onRewardedVideoClicked(@NonNull String adUnitId) {
    MoPubAdapterRewardedListener listener = mListeners.get(adUnitId);
    if (listener != null) {
      listener.onRewardedVideoClicked(adUnitId);
    }
  }

  @Override
  public void onRewardedVideoCompleted(
      @NonNull Set<String> adUnitIds, @NonNull MoPubReward reward) {
    for (String adUnitId : adUnitIds) {
      MoPubAdapterRewardedListener listener = mListeners.get(adUnitId);
      if (listener != null) {
        HashSet<String> set = new HashSet<>();
        set.add(adUnitId);
        listener.onRewardedVideoCompleted(set, reward);
      }
    }
  }

  @Override
  public void onRewardedVideoClosed(@NonNull String adUnitId) {
    MoPubAdapterRewardedListener listener = mListeners.get(adUnitId);
    if (listener != null) {
      listener.onRewardedVideoClosed(adUnitId);
    }
    mListeners.remove(adUnitId);
  }

  // SofHashMap implementation from
  // https://www.javaspecialists.eu/archive/Issue098.html
  public static class SoftHashMap <K, V> extends AbstractMap<K, V>
          implements Serializable {
    /** The internal HashMap that will hold the SoftReference. */
    private final Map<K, SoftReference<V>> hash =
            new HashMap<K, SoftReference<V>>();

    private final Map<SoftReference<V>, K> reverseLookup =
            new HashMap<SoftReference<V>, K>();

    /** Reference queue for cleared SoftReference objects. */
    private final ReferenceQueue<V> queue = new ReferenceQueue<V>();

    public V get(Object key) {
      expungeStaleEntries();
      V result = null;
      // We get the SoftReference represented by that key
      SoftReference<V> soft_ref = hash.get(key);
      if (soft_ref != null) {
        // From the SoftReference we get the value, which can be
        // null if it has been garbage collected
        result = soft_ref.get();
        if (result == null) {
          // If the value has been garbage collected, remove the
          // entry from the HashMap.
          hash.remove(key);
          reverseLookup.remove(soft_ref);
        }
      }
      return result;
    }

    private void expungeStaleEntries() {
      Reference<? extends V> sv;
      while ((sv = queue.poll()) != null) {
        hash.remove(reverseLookup.remove(sv));
      }
    }

    public V put(K key, V value) {
      expungeStaleEntries();
      SoftReference<V> soft_ref = new SoftReference<V>(value, queue);
      reverseLookup.put(soft_ref, key);
      SoftReference<V> result = hash.put(key, soft_ref);
      if (result == null) return null;
      reverseLookup.remove(result);
      return result.get();
    }

    public V remove(Object key) {
      expungeStaleEntries();
      SoftReference<V> result = hash.remove(key);
      if (result == null) return null;
      return result.get();
    }

    public void clear() {
      hash.clear();
      reverseLookup.clear();
    }

    public int size() {
      expungeStaleEntries();
      return hash.size();
    }

    /**
     * Returns a copy of the key/values in the map at the point of
     * calling.  However, setValue still sets the value in the
     * actual SoftHashMap.
     */
    public Set<Entry<K,V>> entrySet() {
      expungeStaleEntries();
      Set<Entry<K,V>> result = new LinkedHashSet<Entry<K, V>>();
      for (final Entry<K, SoftReference<V>> entry : hash.entrySet()) {
        final V value = entry.getValue().get();
        if (value != null) {
          result.add(new Entry<K, V>() {
            public K getKey() {
              return entry.getKey();
            }
            public V getValue() {
              return value;
            }
            public V setValue(V v) {
              entry.setValue(new SoftReference<V>(v, queue));
              return value;
            }
          });
        }
      }
      return result;
    }
  }
}

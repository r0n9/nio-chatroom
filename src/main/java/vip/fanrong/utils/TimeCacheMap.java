/*
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements. See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership. The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License. You may obtain a copy of the License at
  <p>
  http://www.apache.org/licenses/LICENSE-2.0
  <p>
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package vip.fanrong.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Expires keys that have not been updated in the configured number of seconds.
 * The algorithm used will take between expirationSecs and
 * expirationSecs * (1 + 1 / (numBuckets-1)) to actually expire the message.
 * <p>
 * get, put, remove, containsKey, and size take O(numBuckets) time to run.
 * <p>
 * The advantage of this design is that the expiration thread only locks the object
 * for O(1) time, meaning the object is essentially always available for gets/puts.
 */
public class TimeCacheMap<K, V> {
    //this default ensures things expire at most 50% past the expiration time
    private static final int DEFAULT_NUM_BUCKETS = 3;

    public interface ExpiredCallback<K, V> {
        void expire(K key, V val);
    }

    private LinkedList<HashMap<K, V>> _buckets;

    private final Object _lock = new Object();
    private Thread _cleaner;
    private ExpiredCallback<K, V> _callback;

    public TimeCacheMap(int expirationSecs, int numBuckets, ExpiredCallback<K, V> callback) {
        if (numBuckets < 2) {
            throw new IllegalArgumentException("numBuckets must be >= 2");
        }
        _buckets = new LinkedList<>();
        for (int i = 0; i < numBuckets; i++) {
            _buckets.add(new HashMap<>());
        }


        _callback = callback;
        final long expirationMillis = expirationSecs * 1000L;
        final long sleepTime = expirationMillis / (numBuckets - 1);
        _cleaner = new Thread(new Runnable() {
            public void run() {
                try {
                    while (true) {
                        Map<K, V> dead;
                        Thread.sleep(sleepTime);
                        synchronized (_lock) {
                            dead = _buckets.removeLast();
                            _buckets.addFirst(new HashMap<>());
                        }
                        if (_callback != null) {
                            for (Entry<K, V> entry : dead.entrySet()) {
                                _callback.expire(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                } catch (InterruptedException ex) {

                }
            }
        });
        _cleaner.setDaemon(true);
        _cleaner.start();
    }

    public TimeCacheMap(int expirationSecs, ExpiredCallback<K, V> callback) {
        this(expirationSecs, DEFAULT_NUM_BUCKETS, callback);
    }

    public TimeCacheMap(int expirationSecs) {
        this(expirationSecs, DEFAULT_NUM_BUCKETS);
    }

    public TimeCacheMap(int expirationSecs, int numBuckets) {
        this(expirationSecs, numBuckets, null);
    }


    public boolean containsKey(K key) {
        synchronized (_lock) {
            for (HashMap<K, V> bucket : _buckets) {
                if (bucket.containsKey(key)) {
                    return true;
                }
            }
            return false;
        }
    }

    public V get(K key) {
        synchronized (_lock) {
            for (HashMap<K, V> bucket : _buckets) {
                if (bucket.containsKey(key)) {
                    return bucket.get(key);
                }
            }
            return null;
        }
    }

    public void put(K key, V value) {
        synchronized (_lock) {
            Iterator<HashMap<K, V>> it = _buckets.iterator();
            HashMap<K, V> bucket = it.next();
            bucket.put(key, value);
            while (it.hasNext()) {
                bucket = it.next();
                bucket.remove(key);
            }
        }
    }

    public V remove(K key) {
        synchronized (_lock) {
            for (HashMap<K, V> bucket : _buckets) {
                if (bucket.containsKey(key)) {
                    return bucket.remove(key);
                }
            }
            return null;
        }
    }

    public int size() {
        synchronized (_lock) {
            int size = 0;
            for (HashMap<K, V> bucket : _buckets) {
                size += bucket.size();
            }
            return size;
        }
    }

    public Set<K> keySet() {
        synchronized (_lock) {
            Set<K> set = new HashSet<>();
            for (HashMap<K, V> bucket : _buckets) {
                set.addAll(bucket.keySet());
            }
            return set;
        }
    }

    public void cleanup() {
        _cleaner.interrupt();
    }
}

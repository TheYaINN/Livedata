package livedata;

import java.util.*;

public class SafeIterable<K, V> implements Iterable<Map.Entry<K, V>> {

    Entry<K, V> mStart;
    private Entry<K, V> mEnd;
    // using WeakHashMap over List<WeakReference>, so we don't have to manually remove
    // WeakReferences that have null in them.
    private WeakHashMap<SupportRemove<K, V>, Boolean> mIterators = new WeakHashMap<>();
    private int mSize = 0;
    private HashMap<K, Entry<K, V>> mHashMap = new HashMap<>();

    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        ListIterator<K, V> iterator = new AscendingIterator<>(mStart, mEnd);
        mIterators.put(iterator, false);
        return iterator;
    }

    public Map.Entry<K, V> ceil(K k) {
        if (contains(k)) {
            return mHashMap.get(k).mPrevious;
        }
        return null;
    }

    protected Entry<K, V> put(K key,V v) {
        Entry<K, V> newEntry = new Entry<>(key, v);
        mSize++;
        if (mEnd == null) {
            mStart = newEntry;
            mEnd = mStart;
            return newEntry;
        }

        mEnd.mNext = newEntry;
        newEntry.mPrevious = mEnd;
        mEnd = newEntry;
        return newEntry;
    }

    public IteratorWithAdditions iteratorWithAdditions() {
        @SuppressWarnings("unchecked")
        IteratorWithAdditions iterator = new IteratorWithAdditions();
        mIterators.put(iterator, false);
        return iterator;
    }

    public Map.Entry<K, V> eldest() {
        return mStart;
    }

    public Map.Entry<K, V> newest() {
        return mEnd;
    }

    public int size() {
        return mSize;
    }

    public V remove(K key) {
        Entry<K, V> toRemove = get(key);
        if (toRemove == null) {
            return null;
        }
        mSize--;
        if (!mIterators.isEmpty()) {
            for (SupportRemove<K, V> iter : mIterators.keySet()) {
                iter.supportRemove(toRemove);
            }
        }

        if (toRemove.mPrevious != null) {
            toRemove.mPrevious.mNext = toRemove.mNext;
        } else {
            mStart = toRemove.mNext;
        }

        if (toRemove.mNext != null) {
            toRemove.mNext.mPrevious = toRemove.mPrevious;
        } else {
            mEnd = toRemove.mPrevious;
        }

        toRemove.mNext = null;
        toRemove.mPrevious = null;
        return toRemove.mValue;
    }

    private static class DescendingIterator<K, V> extends ListIterator<K, V> {

        DescendingIterator(Entry<K, V> start, Entry<K, V> expectedEnd) {
            super(start, expectedEnd);
        }

        @Override
        Entry<K, V> forward(Entry<K, V> entry) {
            return entry.mPrevious;
        }

        @Override
        Entry<K, V> backward(Entry<K, V> entry) {
            return entry.mNext;
        }
    }

    public Iterator<Map.Entry<K, V>> descendingIterator() {
        DescendingIterator<K, V> iterator = new DescendingIterator<>(mEnd, mStart);
        mIterators.put(iterator, false);
        return iterator;
    }

    public boolean contains(K key) {
        return mHashMap.containsKey(key);
    }

    public V putIfAbsent(K key,V v) {
        Entry<K, V> current = get(key);
        if (current != null) {
            return current.mValue;
        }
        mHashMap.put(key, put(key, v));
        return null;
    }

    protected Entry<K, V> get(K k) {
        return mHashMap.get(k);
    }

    @Override
    public Spliterator<Map.Entry<K, V>> spliterator() {
        return null;
    }

    interface SupportRemove<K, V> {
        void supportRemove(Entry<K, V> entry);
    }

    private class IteratorWithAdditions implements Iterator<Map.Entry<K, V>>, SupportRemove<K, V> {
        private Entry<K, V> mCurrent;
        private boolean mBeforeStart = true;

        IteratorWithAdditions() {
        }

        @SuppressWarnings("ReferenceEquality")
        @Override
        public void supportRemove(Entry<K, V> entry) {
            if (entry == mCurrent) {
                mCurrent = mCurrent.mPrevious;
                mBeforeStart = mCurrent == null;
            }
        }

        @Override
        public boolean hasNext() {
            if (mBeforeStart) {
                return mStart != null;
            }
            return mCurrent != null && mCurrent.mNext != null;
        }

        @Override
        public Map.Entry<K, V> next() {
            if (mBeforeStart) {
                mBeforeStart = false;
                mCurrent = mStart;
            } else {
                mCurrent = mCurrent != null ? mCurrent.mNext : null;
            }
            return mCurrent;
        }
    }

    private abstract static class ListIterator<K, V> implements Iterator<Map.Entry<K, V>>, SupportRemove<K, V> {
        Entry<K, V> mExpectedEnd;
        Entry<K, V> mNext;

        ListIterator(Entry<K, V> start, Entry<K, V> expectedEnd) {
            this.mExpectedEnd = expectedEnd;
            this.mNext = start;
        }

        @Override
        public boolean hasNext() {
            return mNext != null;
        }

        @SuppressWarnings("ReferenceEquality")
        @Override
        public void supportRemove(Entry<K, V> entry) {
            if (mExpectedEnd == entry && entry == mNext) {
                mNext = null;
                mExpectedEnd = null;
            }

            if (mExpectedEnd == entry) {
                mExpectedEnd = backward(mExpectedEnd);
            }

            if (mNext == entry) {
                mNext = nextNode();
            }
        }

        @SuppressWarnings("ReferenceEquality")
        private Entry<K, V> nextNode() {
            if (mNext == mExpectedEnd || mExpectedEnd == null) {
                return null;
            }
            return forward(mNext);
        }

        @Override
        public Map.Entry<K, V> next() {
            Map.Entry<K, V> result = mNext;
            mNext = nextNode();
            return result;
        }

        abstract Entry<K, V> forward(Entry<K, V> entry);

        abstract Entry<K, V> backward(Entry<K, V> entry);
    }

    static class AscendingIterator<K, V> extends SafeIterable.ListIterator<K, V> {
        AscendingIterator(Entry<K, V> start, Entry<K, V> expectedEnd) {
            super(start, expectedEnd);
        }

        @Override
        Entry<K, V> forward(Entry<K, V> entry) {
            return entry.mNext;
        }

        @Override
        Entry<K, V> backward(Entry<K, V> entry) {
            return entry.mPrevious;
        }
    }

    static class Entry<K, V> implements Map.Entry<K, V> {
        final K mKey;
        final V mValue;
        Entry<K, V> mNext;
        Entry<K, V> mPrevious;

        Entry(K key, V value) {
            mKey = key;
            this.mValue = value;
        }

        @Override
        public K getKey() {
            return mKey;
        }

        @Override
        public V getValue() {
            return mValue;
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException("An entry modification is not supported");
        }

        @Override
        public String toString() {
            return mKey + "=" + mValue;
        }

        @SuppressWarnings("ReferenceEquality")
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Entry)) {
                return false;
            }
            Entry entry = (Entry) obj;
            return mKey.equals(entry.mKey) && mValue.equals(entry.mValue);
        }

        @Override
        public int hashCode() {
            return mKey.hashCode() ^ mValue.hashCode();
        }
    }
}

package livedata;

public interface LifecycleEventObserver extends LifecycleObserver{

    void onStateChanged(LifecycleOwner source, Lifecycle.Event event);

}

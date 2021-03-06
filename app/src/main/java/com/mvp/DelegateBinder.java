package com.mvp;

import android.os.Bundle;

public interface DelegateBinder<V extends MvpView, P extends MvpPresenter<V>> {
    void onCreate(Bundle savedInstanceState);
    void onPostResume();
    void onDestroy();
    void onSaveInstanceState(Bundle outState);
    P getPresenter();
    void setOnPresenterLoadedListener(OnPresenterLoadedListener<V, P> listener);
}

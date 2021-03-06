
package com.lzf.myapplication.dmc;

import android.util.Log;

import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.support.avtransport.callback.Pause;

public class PauseCallback extends Pause {
    public PauseCallback(Service paramService) {
        super(paramService);
    }

    public void failure(ActionInvocation paramActionInvocation, UpnpResponse paramUpnpResponse,
                        String paramString) {
        Log.e("pause failed", "pause failed");
    }

    public void success(ActionInvocation paramActionInvocation) {
        super.success(paramActionInvocation);
        Log.e("pause success", "pause success");
    }

}

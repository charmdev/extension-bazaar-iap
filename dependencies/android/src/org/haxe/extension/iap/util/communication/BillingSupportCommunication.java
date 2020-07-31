package org.haxe.extension.iap.util.communication;


import org.haxe.extension.iap.util.IabResult;

public interface BillingSupportCommunication {
    void onBillingSupportResult(int response);
    void remoteExceptionHappened(IabResult result);
}

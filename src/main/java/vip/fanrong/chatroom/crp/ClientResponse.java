package vip.fanrong.chatroom.crp;

import vip.fanrong.utils.JSONUtil;

/**
 * @author r0n9 <fanrong330@gmail.com>
 *
 */
public class ClientResponse {

    private ClientRequest.ClientCMD requestCmd;
    private int status;

    private String responseContent;

    public ClientResponse fromMessage(String msg) {
        return (ClientResponse) JSONUtil.fromJson(msg, ClientResponse.class);
    }

    public String toMessage() {
        return JSONUtil.toString(this, ClientResponse.class);
    }

    public ClientRequest.ClientCMD getRequestCmd() {
        return requestCmd;
    }

    public void setRequestCmd(ClientRequest.ClientCMD requestCmd) {
        this.requestCmd = requestCmd;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getResponseContent() {
        return responseContent;
    }

    public void setResponseContent(String responseContent) {
        this.responseContent = responseContent;
    }


}

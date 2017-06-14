/**
 * Copyright Nielsen. All Rights Reserved. This software is the proprietary information of Nielsen.
 * Use is subject to license terms.
 */
package vip.fanrong.chatroom.cmd;

import java.util.Date;

/**
 * @author fangro01
 *
 */
public class Quit {
    private String userName;

    private Date date;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }


}

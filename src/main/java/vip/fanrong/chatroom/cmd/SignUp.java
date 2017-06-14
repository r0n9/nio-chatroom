package vip.fanrong.chatroom.cmd;

import java.util.Date;

/**
 * @author fangro01
 *
 */
public class SignUp {

    private String name;
    private String password;

    private Date date;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }


}

package com.chris.comments.utils.interceptor;

import com.chris.comments.entity.User;

public class UserHolderV1 {
    private static final ThreadLocal<User> tl = new ThreadLocal<>();

    public static void saveUser(User user){
        tl.set(user);
    }

    public static User getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}

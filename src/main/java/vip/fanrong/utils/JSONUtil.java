package vip.fanrong.utils;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

/**
 * @author r0n9 <fanrong330@gmail.com>
 *
 */
public class JSONUtil {
    private static final Logger LOGGER = Logger.getLogger(JSONUtil.class);

    public static void outputObject(OutputStream os, Object obj, Type typeofSrc) {
        JsonWriter writer = null;
        try {
            writer = new JsonWriter(new OutputStreamWriter(os, "UTF-8"));
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();
            gson.toJson(obj, typeofSrc, writer);
        } catch (Exception e) {
            LOGGER.error(e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception e) {

                } finally {
                    writer = null;
                }
            }
        }
    }

    public static String toString(Object obj, Type typeofSrc) {
        try {
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();
            return gson.toJson(obj, typeofSrc);
        } catch (Exception e) {
            LOGGER.error("error when parse response:" + obj, e);
            return null;
        }
    }

    public static Object fromJson(String jsonStr, Type typeofSrc) {
        try {
            return new Gson().fromJson(jsonStr, typeofSrc);
        } catch (Exception e) {
            LOGGER.error("error when parse jsonStr:" + jsonStr, e);
            return null;
        }
    }
}

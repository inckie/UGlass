package damn.com.shared;

import org.json.JSONException;
import org.json.JSONObject;

public class TokenMessage {

    public String token;
    public long expiresAt; // timestamp, ms
    public boolean sandBox;

    public boolean isValid() {
        // time should be in sync between devices
        return System.currentTimeMillis() < expiresAt;
    }

    // don't want to use 3rd party serializers there

    public String toJson() {
        try {
            JSONObject jo = new JSONObject();
            jo.put("token", token);
            jo.put("expiresAt", expiresAt);
            jo.put("sandBox", sandBox);
            return  jo.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static TokenMessage fromJson(String str){
        try {
            JSONObject jo = new JSONObject(str);
            TokenMessage res = new TokenMessage();
            res.token = jo.getString("token");
            res.expiresAt = jo.getLong("expiresAt");
            res.sandBox = jo.getBoolean("sandBox");
            return res;
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}

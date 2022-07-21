import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class taobao {
    //淘口令网信息
    public static String username = "111111";//淘口令网用户名
    public static String password = "111111";//淘口令网密码
    public static String cookie = "";//不用改
    //淘宝客信息
    public static String pid = "mm_111111_111111_111111";//淘宝联盟pid


    public static void main(String[] args) throws Exception {
        try {
            taobao t = new taobao();
            System.out.println(t.command2id(""));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void login() throws IOException {
        OkHttpClient okHttpClient = new OkHttpClient();
        RequestBody requestBody = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("remember", "true")
                .build();
        Request request = new Request.Builder()
                .url("https://www.taokouling.com/user/login/")
                .post(requestBody)
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            cookie = response.header("Set-Cookie");
        }
    }

    private void try_login() throws Exception {
        int count = 0;
        while (is_cookie_expired() & count < 5) {
            login();
            count++;
        }
        if (count >= 5)
            throw new Exception("登录失败");
    }

    private boolean is_cookie_expired() throws ParseException {
        if (cookie.equals(""))
            return true;
        Pattern p = Pattern.compile("(?<=(expires=)).*?(?=;)");
        Matcher matcher = p.matcher(cookie);
        if (matcher.find()) {
            SimpleDateFormat sf = new SimpleDateFormat("EEE, d-MMM-yyyy HH:mm:ss z", Locale.US);
            Date cookie_expire_time = sf.parse(matcher.group());
            return cookie_expire_time.before(new Date());
        }
        return true;
    }

    private String command2id(String command) throws Exception {
        Pattern p = Pattern.compile("(https?)://[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]");
        Matcher matcher = p.matcher(command);
        if (matcher.find())
            command = matcher.group();
        else
            throw new IOException("没有找到网址,需要更新匹配方式");
        Document doc = Jsoup.connect(command).followRedirects(true).get();
        //用户分享链接
        Element e = doc.getElementsByTag("script").get(1);
        String s = e.data();
        if (s.equals(""))
            return encryption_url2id(command);
        int a = s.indexOf("var url = '") + "var url = '".length();
        int b = s.indexOf("'", a);
        s = s.substring(a, b);
        return url2id(s);
    }

    private String encryption_url2id(String command) throws Exception {
        String encryption_url;
        String true_url;
        //有优惠券的二合一链接
        try (WebClient webClient = new WebClient()) {
            Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);
            Logger.getLogger("org.apache.http.client.protocol.ResponseProcessCookies").setLevel(Level.SEVERE);
            webClient.getOptions().setThrowExceptionOnScriptError(false);
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            HtmlPage page = webClient.getPage(command);
            Document doc = Jsoup.parse(page.asXml());
            Element e = doc.selectFirst("#J_u_root > div > div:nth-child(2) > a");
            if (e == null)
                throw new Exception("二合一链接解析失败");
            encryption_url = e.attr("href");//商品加密链接
        }
        //淘宝会先请求加密链接,得到需要跳转的链接,然后跳转链接加上referer得到真实链接
        Document doc = Jsoup.connect(encryption_url).followRedirects(true).get();
        String jump_url = doc.toString();//跳转链接
        int a = jump_url.indexOf("var real_jump_address = '") + "var real_jump_address = '".length();
        int b = jump_url.indexOf("'", a);
        jump_url = jump_url.substring(a, b);
        //去除跳转链接中的 amp;
        jump_url = jump_url.replace("amp;", "");
        //从302请求获得真实商品链接
        OkHttpClient okHttpClient = new OkHttpClient.Builder().followRedirects(false).build();
        Request request = new Request.Builder()
                .url(jump_url)
                .addHeader("Referer", encryption_url)
                .get()
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.code() == 302)
                true_url = response.header("Location");
            else
                throw new Exception("真实商品链接解析失败");
        }
        String decode_url = URLDecoder.decode(Objects.requireNonNull(true_url), StandardCharsets.UTF_8);
        a = decode_url.indexOf("https://detail.m.tmall.com");
        decode_url = decode_url.substring(a);
        return url2id(decode_url);
    }

    private String url2id(String url) throws Exception {
        String itemId = null;
        if (url.indexOf('?') != -1) {
            final String contents = url.substring(url.indexOf('?') + 1);
            String[] keyValues = contents.split("&");
            for (String keyValue : keyValues) {
                String key = keyValue.substring(0, keyValue.indexOf("="));
                String value = keyValue.substring(keyValue.indexOf("=") + 1);
                if (key.equals("id") & !value.equals("")) {
                    itemId = value;
                }
            }
        }
        if (itemId == null) {
            throw new Exception("链接转换商品id失败");
        }
        return itemId;
    }

    private String tkl2name(String tkl) throws Exception {
        OkHttpClient okHttpClient = new OkHttpClient();
        RequestBody requestBody = new FormBody.Builder()
                .add("text", tkl)
                .build();
        Request request = new Request.Builder()
                .url("https://www.taokouling.com/index/taobao_tkljm")
                .post(requestBody)
                .addHeader("Cookie", cookie)
                .addHeader("x-requested-with", "XMLHttpRequest")
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String result = Objects.requireNonNull(response.body()).string();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(result);
                JsonNode data = node.get("data");
                return data.get("content").asText();
            } else
                throw new Exception("获取商品详情出错");
        }
    }

    public String getShortCouponClick(String message) throws Exception {
        try_login();
        String itemId;
        if (message.startsWith("http"))
            itemId = url2id(message);
        else
            itemId = command2id(message);
        OkHttpClient okHttpClient = new OkHttpClient();
        RequestBody requestBody = new FormBody.Builder()
                .add("url", "https://item.taobao.com/item.htm?id=" + itemId)
                .add("urltype", "1")
                .add("tgdl", "true")
                .add("sckl", "true")
                .add("sdwz", "true")
                .add("tkltitle", "这是淘口令")
                .add("tklpic", "https://gw.alicdn.com/tfs/TB1c.wHdh6I8KJjy0FgXXXXzVXa-580-327.png")
                .add("pid", pid)
                .add("tkluserid", "")
                .build();
        Request request = new Request.Builder()
                .url("https://www.taokouling.com/index/tbtklscspgy/")
                .post(requestBody)
                .addHeader("Cookie", cookie)
                .addHeader("x-requested-with", "XMLHttpRequest")
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String result = Objects.requireNonNull(response.body()).string();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(result);
                JsonNode data = node.get("data");
                data = data.get(0);
                String short_url = data.get("url").asText();
                String tkl = data.get("tkl").asText();
                String name = tkl2name(tkl);
                return "下单链接:" + short_url + " " + tkl + " " + name;
            } else
                throw new Exception("获取优惠券链接出错");
        }
    }

    public boolean is_auth_expired() throws Exception {
        try_login();
        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://www.taokouling.com/user/taobao_oauth/")
                .get()
                .addHeader("Cookie", cookie)
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String result = Objects.requireNonNull(response.body()).string();
                Document doc = Jsoup.parse(result);
                Element e = doc.selectFirst("#info > div > div:nth-child(4) > input");
                if (e != null) {
                    String time = e.attr("placeholder");
                    SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-d HH:mm");
                    Date auth_expire_time = sf.parse(time);
                    return auth_expire_time.before(new Date());
                }
            }
        }
        throw new Exception("获取授权时间出错");
    }
}


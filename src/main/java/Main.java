import net.mamoe.mirai.Bot;
import net.mamoe.mirai.BotFactory;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.utils.BotConfiguration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    // 输入管理员和机器人信息
    public static Long botQQ = 111111L;//机器人qq号
    public static Long adminQQ = 111111L;//管理员qq号
    public static String botPassword = "111111";//机器人qq密码

    public static void main(String[] args) throws Exception {
        Bot bot =
                BotFactory.INSTANCE.newBot(
                        botQQ,
                        botPassword,
                        new BotConfiguration() {
                            {
                                fileBasedDeviceInfo(); // 使用 device.json 存储设备信息
                            }
                        });
        bot.login();
        admin_robot(bot);
        taobao_robot(bot);
    }

    public static void admin_robot(Bot bot) throws Exception {
        Friend admin = bot.getFriend(adminQQ);
        if (admin == null)
            throw new Exception("没有找到管理员");
        admin.sendMessage("机器人已经登录");
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(
                new check_auth(admin),
                0,
                12,
                TimeUnit.HOURS);
        bot.getEventChannel()
                .subscribeAlways(
                        FriendMessageEvent.class,
                        (event) -> {
                            Friend sender = event.getSender();
                            if (sender.getId() == adminQQ) {
                                String text = event.getMessage().contentToString();
                                // 管理员发送？获得提示，修改session
                                if (text.equals("?") | text.equals("？")) {
                                    sender.sendMessage("授权地址:http://taobao.taokouling.com/");
                                }
                            }
                        });
    }

    public static void taobao_robot(Bot bot) {
        bot.getEventChannel()
                .subscribeAlways(
                        FriendMessageEvent.class,
                        (event) -> {
                            Friend sender = event.getSender();
                            String text = event.getMessage().contentToString();
                            if (!(text.startsWith("?") | text.startsWith("？"))) {
                                try {
                                    String result = new taobao().getShortCouponClick(text);
                                    sender.sendMessage(result);
                                } catch (Exception e) {
                                    sender.sendMessage(e.getMessage());
                                }
                            }
                        });
    }
}

class check_auth implements Runnable {
    private final Friend admin;

    public check_auth(Friend admin) {
        this.admin = admin;
    }

    @Override
    public void run() {
        try {
            if (new taobao().is_auth_expired())
                admin.sendMessage("淘宝联盟需要更新授权\n网址:http://taobao.taokouling.com/");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
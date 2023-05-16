package com.inmaytide.orbit.gateway.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

/**
 * @author inmaytide
 * @since 2023/5/15
 **/
public class CaptchaGenerator {

    private final static String IMAGE_FORMAT = "png";

    private final static int IMAGE_WIDTH = 160;

    private final static int IMAGE_HEIGHT = 80;

    private final static char[] CHARS = {'1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};

    private final static int[] TEXT_STYLE = new int[]{Font.BOLD, Font.ITALIC, Font.PLAIN};

    private final static String[] FONT_NAMES = new String[]{"Annai MIN", "Bradley Hand", "Times New Roman", "Savoye LET", "Herculanum", "Party LET"};

    public static String generate(OutputStream os) throws IOException {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        // 获取图形上下文
        Graphics2D g = (Graphics2D) image.getGraphics();
        //生成随机类
        Random random = new Random();
        // 设定背景色
        g.setColor(getRandColor(180, 220));
        g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
        // 随机产生干扰线，使图象中的认证码不易被其它程序探测到
        for (int i = 0; i < 168; i++) {
            g.setStroke(new BasicStroke(random.nextFloat(1)));
            g.setColor(getRandColor(110, 250));
            int x = random.nextInt(IMAGE_WIDTH);
            int y = random.nextInt(IMAGE_HEIGHT);
            int xl = random.nextInt(12);
            int yl = random.nextInt(12);
            g.drawLine(x, y, x + xl, y + yl);
        }
        //取随机产生的码
        StringBuilder code = new StringBuilder();
        //4代表4位验证码,如果要生成更多位的认证码,则加大数值
        for (int i = 0; i < 4; ++i) {
            code.append(CHARS[(int) (CHARS.length * Math.random())]);
            // 将认证码显示到图象中
            g.setColor(new Color(random.nextInt(180), random.nextInt(200), random.nextInt(170)));
            // 直接生成
            String str = code.substring(i, i + 1);
            //设定字体
            g.setFont(new Font(FONT_NAMES[random.nextInt(FONT_NAMES.length)], TEXT_STYLE[random.nextInt(TEXT_STYLE.length)], 30));
            // 设置随便码在背景图图片上的位置
            g.drawString(str, 30 * i + 20, 50);
        }
        // 释放图形上下文
        g.dispose();
        ImageIO.write(image, IMAGE_FORMAT, os);
        return code.toString();
    }

    //给定范围获得随机颜色
    static Color getRandColor(int fc, int bc) {
        Random random = new Random();
        if (fc > 255) fc = 255;
        if (bc > 255) bc = 255;
        int r = fc + random.nextInt(bc - fc);
        int g = fc + random.nextInt(bc - fc);
        int b = fc + random.nextInt(bc - fc);
        return new Color(r, g, b);
    }
}

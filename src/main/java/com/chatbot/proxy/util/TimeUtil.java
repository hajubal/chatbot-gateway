package com.chatbot.proxy.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class TimeUtil {

  public static String convert(String unixTimeMillis) {
    return convert(Long.parseLong(unixTimeMillis));
  }

  public static String convert(long unixTimeMillis) {
    // Date 객체 생성
    Date date = new Date(unixTimeMillis);

    // SimpleDateFormat을 사용하여 시간 형식 지정
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    // 한국 표준시(KST) 설정
    TimeZone kstTimeZone = TimeZone.getTimeZone("Asia/Seoul");
    sdf.setTimeZone(kstTimeZone);

    // 포맷된 시간 출력
    return sdf.format(date);
  }

}

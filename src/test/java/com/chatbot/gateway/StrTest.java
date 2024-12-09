package com.chatbot.gateway;

public class StrTest {

  public static void main(String[] args) {
    String str = "test " + System.lineSeparator();

    System.out.println("str = '" + str + "'");
    System.out.println("str = '" + str.trim() + "'");

    System.out.println("str = " + str.lastIndexOf(System.lineSeparator()));

    String replaced = str.substring(0, str.lastIndexOf(System.lineSeparator()));

    System.out.println("replaced = " + replaced);
  }

}

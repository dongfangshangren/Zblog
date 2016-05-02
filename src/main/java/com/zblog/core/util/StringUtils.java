package com.zblog.core.util;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StringUtils{

  private StringUtils(){
  }

  public static boolean isBlank(String str){
    return str == null || str.trim().length() == 0;
  }

  public static String join(Collection<String> collect, String delimiter){
    StringBuilder result = new StringBuilder();
    for(String temp : collect){
      result.append(temp).append(delimiter);
    }

    if(!collect.isEmpty())
      result.delete(result.length() - delimiter.length(), result.length());

    return result.toString();
  }

  public static String trimLeft(String source){
    if(isBlank(source))
      return null;

    int index = 0;
    for(; index < source.length(); index++){
      if(source.charAt(index) > 32)
        break;
    }

    return source.substring(index);
  }

  public static String trimRight(String source){
    if(isBlank(source))
      return null;

    int index = source.length() - 1;
    for(; index >= 0; index--){
      if(source.charAt(index) > 32)
        break;
    }

    return source.substring(0, index + 1);
  }

  public static String emptyDefault(String ifEmpty, String defaults){
    return isBlank(ifEmpty) ? defaults : ifEmpty;
  }

 
}

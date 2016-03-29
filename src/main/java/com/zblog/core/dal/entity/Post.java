package com.zblog.core.dal.entity;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import com.zblog.core.dal.constants.PostConstants;

/**
 * 文章/页面,注：post的creator为userid
 * 
 * @author zhou
 * 
 */
public class Post extends BaseEntity{
  private String title;
  /* 摘录,当type为页面该项为null */
  private String excerpt;
  private String content;
  /* 文章类型（post/page等） */
  private String type = PostConstants.TYPE_POST;
  /* 父文章ID，主要用于PAGE,只支持两级 */
  private String parent = PostConstants.DEFAULT_PARENT;
  /* 分类ID,主要用于POST */
  private String categoryid;
  /* 文章状态 */
  private String pstatus = PostConstants.POST_PUBLISH;
  /* 评论状态 */
  private String cstatus = PostConstants.COMMENT_OPEN;
  /* 评论数 */
  private int ccount = 0;
  /* 阅读数 */
  private int rcount = 0;

  public String getTitle(){
    return title;
  }

  public void setTitle(String title){
    this.title = title;
  }

  public String getExcerpt(){
    return excerpt;
  }

  public void setExcerpt(String excerpt){
    this.excerpt = excerpt;
  }

  public String getContent(){
    return content;
  }

  public void setContent(String content){
    this.content = content;
  }

  public String getType(){
    return type;
  }

  public void setType(String type){
    this.type = type;
  }

  public String getParent(){
    return parent;
  }

  public void setParent(String parent){
    this.parent = parent;
  }

  public String getCategoryid(){
    return categoryid;
  }

  public void setCategoryid(String categoryid){
    this.categoryid = categoryid;
  }

  public String getPstatus(){
    return pstatus;
  }

  public void setPstatus(String pstatus){
    this.pstatus = pstatus;
  }

  public String getCstatus(){
    return cstatus;
  }

  public void setCstatus(String cstatus){
    this.cstatus = cstatus;
  }

  public int getCcount(){
    return ccount;
  }

  public void setCcount(int ccount){
    this.ccount = ccount;
  }

  public int getRcount(){
    return rcount;
  }

  public void setRcount(int rcount){
    this.rcount = rcount;
  }

@Override
public String toString() {
	return ReflectionToStringBuilder.toString(this); 
}

  
  
  
}

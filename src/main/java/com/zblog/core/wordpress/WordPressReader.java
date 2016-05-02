package com.zblog.core.wordpress;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.zblog.core.plugin.MapContainer;
import com.zblog.core.util.DateUtils;

/**
 * WordPress导入工具
 * 
 * @author zhou
 *
 */
public final class WordPressReader{
  private static final Logger logger = LoggerFactory.getLogger(WordPressReader.class);

  private WordPressReader(){
  }

  /**
   * 从wordpress的xml站点文件中加载数据
   * 
   * @param xml
   * @return 返回有序链表，依次为domain、attachment、post
   */
  public static List<MapContainer> load(InputStream xml){
    List<MapContainer> list = new ArrayList<>();

    try{
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();

      Document doc = builder.parse(xml);

      XPath xpath = XPathFactory.newInstance().newXPath();
      String domain = (String) xpath.evaluate("/rss/channel/link/text()", doc, XPathConstants.STRING);
      MapContainer link = new MapContainer();
      link.put("itemType", "domain");
      link.put("domain", domain);
      list.add(link);

      xpath.reset();
      NodeList items = (NodeList) xpath.evaluate("/rss/channel/item", doc, XPathConstants.NODESET);
      for(int i = 0; i < items.getLength(); i++){
        Element item = (Element) items.item(i);
        MapContainer map = new MapContainer();
        String title = item.getElementsByTagName("title").item(0).getTextContent();
        map.put("title", title);
        String pubDate = item.getElementsByTagName("pubDate").item(0).getTextContent();
       // map.put("pubDate", DateUtils.parse(pubDate, "EEE, dd MMM yyyy HH:mm:ss Z", Locale.US));
       //"Sat, 17 Apr 2010 03:33:52 +0000"
        map.put("pubDate", DateUtils.parse("E, dd MMM yyyy HH:mm:ss Z",pubDate, Locale.ENGLISH));
        String itemType = item.getElementsByTagName("wp:post_type").item(0).getTextContent();
        map.put("itemType", itemType);
        if("attachment".equals(itemType)){
          String attachUrl = item.getElementsByTagName("wp:attachment_url").item(0).getTextContent();
          map.put("attachUrl", attachUrl);
          list.add(1, map);
        }else if("post".equals(itemType)){
          String content = item.getElementsByTagName("content:encoded").item(0).getTextContent();
          content = htmlTagAdjust(content);
          map.put("content", content);

          NodeList categorys = item.getElementsByTagName("category");
          for(int j = 0; j < categorys.getLength(); j++){
            Element category = (Element) categorys.item(j);
            String categoryDomain = category.getAttribute("domain");
            if("post_tag".equals(categoryDomain)){
              map.putIfAbsent("tags", new ArrayList<String>()).add(category.getTextContent());
            }else if("category".equals(categoryDomain)){
              map.put("category", category.getTextContent());
            }
          }

          list.add(map);
        }
      }
    }catch(Exception e){
      logger.error("load wordpress xml error", e);
      list = null;
    }finally{
      IOUtils.closeQuietly(xml);
    }

    return list;
  }

  private static String htmlTagAdjust(String html){
    StringBuilder result = new StringBuilder();
    try(BufferedReader reader = new BufferedReader(new StringReader(html))){
      String line = null;
      String tag = null;
      boolean pureText = false;
      while((line = reader.readLine()) != null){
        if(tag == null){
          tag = findStartHtmlTag(line.trim());
        }

        if(tag != null){
          if(pureText){
            result.delete(result.length() - 4, result.length());
            result.append("</p>\n");
            pureText = false;
          }

          result.append(line).append('\n');
          tag = line.trim().endsWith("</" + tag + ">") ? null : tag;
          continue;
        }

        if(!pureText){
          if(line.trim().length() != 0){
            result.append("<p>\n").append(line).append("<br>");
            pureText = true;
          }
        }else{
          if(line.trim().length() == 0){
            result.delete(result.length() - 4, result.length());
            result.append("</p>\n");
            pureText = false;
          }else{
            result.append(line).append("<br>");
          }
        }
      }

      if(pureText){
        result.delete(result.length() - 4, result.length());
        result.append("</p>");
        pureText = false;
      }
    }catch(Exception impossible){
      impossible.printStackTrace();
    }

    return result.toString();
  }

  private static String findStartHtmlTag(String text){
    if(text.length() == 0 || text.charAt(0) != '<')
      return null;

    String result = null;
    /* 这里设置html标签字符最长为10 */
    for(int i = 1; i < text.length() && i < 10; i++){
      char c = text.charAt(i);
      if(c == '>'){
        result = text.substring(1, i);
        result = result.matches("[a-zA-Z]+") ? result : null;
        break;
      }
    }

    return result;
  }

  static String htmlImageOrLinkAdjust(String domain, XPath xpath, String xhtml) throws Exception{
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    /* 注意此处必须使用InputSource，不能直接使用String形式参数?? */
    Document doc = builder.parse(new InputSource(new StringReader(xhtml)));

    xpath.reset();
    NodeList eles = (NodeList) xpath.evaluate("//img | //a", doc, XPathConstants.NODESET);
    for(int i = 0; i < eles.getLength(); i++){
      Element ele = (Element) eles.item(i);
      String tagName = ele.getNodeName();
      String linkAttr = "img".equals(tagName) ? "src" : "href";
      String link = ele.getAttribute(linkAttr);
      if(!link.startsWith("http://") && !link.startsWith("https://")){
        ele.setAttribute(linkAttr, domain + link);
      }
    }

    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    StringWriter writer = new StringWriter();
    transformer.transform(new DOMSource(doc), new StreamResult(writer));
    return writer.toString();
  }

}

package com.zblog.biz;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;

import com.zblog.core.WebConstants;
import com.zblog.core.dal.constants.OptionConstants;
import com.zblog.core.dal.constants.PostConstants;
import com.zblog.core.dal.entity.Category;
import com.zblog.core.dal.entity.Post;
import com.zblog.core.dal.entity.Upload;
import com.zblog.core.dal.entity.User;
import com.zblog.core.plugin.MapContainer;
import com.zblog.core.util.FileUtils;
import com.zblog.core.util.JsoupUtils;
import com.zblog.core.util.PostTagHelper;
import com.zblog.core.wordpress.WordPressReader;
import com.zblog.service.CategoryService;
import com.zblog.service.OptionsService;

/**
 * wordpress站点导入，暂只支持导入文章、附件
 * 
 * @author zhou
 *
 */
@Component
public class WordPressManager{
	private static final Logger logger = LoggerFactory.getLogger(WordPressManager.class);
  @Autowired
  private UploadManager uploadManager;
  @Autowired
  private PostManager postManager;
  @Autowired
  private OptionManager optionManager;
  @Autowired
  private CategoryService categoryService;
  @Autowired
  private OptionsService optionsService;

  public void importData(InputStream wordpressXml, User user){
    List<MapContainer> list = WordPressReader.load(wordpressXml);
    Map<String, Upload> links = new HashMap<>();
    /* wordpress站点域名 */
    String wpdomain = null;
    for(MapContainer mc : list){
      String itemType = mc.get("itemType");
      if("domain".equals(itemType)){
        wpdomain = mc.get("domain");
      }else if("attachment".equals(itemType)){
        Upload upload = importAttach(mc, user, wpdomain);
        if(upload != null){
          links.put(mc.getAsString("attachUrl"), upload);
        }
      }else if("post".equals(itemType)){
        importPost(mc, user, wpdomain, links);
      }
    }
  }

  /**
   * 导入指定附件
   * 
   * @param attach
   * @param user
   * @param wpdomain
   *          用于下载图片时防盗链处理
   * @return
   */
  private Upload importAttach(MapContainer attach, User user, String wpdomain){
    String attachUrl = attach.get("attachUrl");
    Date pubDate = attach.get("pubDate");
    InputStream in = null;
    Upload upload = null;
    HttpURLConnection conn = null;
    try{
      conn = (HttpURLConnection) URI.create(attachUrl).toURL().openConnection();
      /* 针对服务器防盗链处理 */
      conn.setRequestProperty("Referer", wpdomain);
      conn.setInstanceFollowRedirects(false);
      conn.connect();
      in = conn.getInputStream();
      upload = uploadManager.insertUpload(new InputStreamResource(in), pubDate,
          FileUtils.getFileNameWithExt(attachUrl), user.getId());
    }catch(Exception e){
      e.printStackTrace();
    }finally{
      IOUtils.closeQuietly(in);
      if(conn != null)
        conn.disconnect();
    }

    return upload;
  }

  /**
   * 导入指定文章
   * 
   * @param post
   */
  private void importPost(MapContainer post, User user, String wpdoamin, Map<String, Upload> links){
    Post p = new Post();
    p.setType(PostConstants.TYPE_POST);
    p.setId(optionManager.getNextPostid());
    p.setCreator(user.getId());
    p.setTitle(post.getAsString("title"));
    Category category = categoryService.loadByName(post.getAsString("category"));
    if(category != null){
      p.setCategoryid(category.getId());
    }else{
      p.setCategoryid(optionsService.getOptionValue(OptionConstants.DEFAULT_CATEGORY_ID));
    }
    p.setCreateTime((Date) post.get("pubDate"));
    p.setLastUpdate(p.getCreateTime());
    String content = post.get("content");
    content = htmlImageOrLinkAdjust(content, wpdoamin, links);
    String cleanTxt = JsoupUtils.plainText(content);
    p.setExcerpt(cleanTxt.length() > PostConstants.EXCERPT_LENGTH ? cleanTxt.substring(0, PostConstants.EXCERPT_LENGTH)
        : cleanTxt);
    p.setContent(content);

    List<String> tags = post.get("tags");
    //logger.debug("post:"+p.toString());
    postManager.insertPost(p, PostTagHelper.from(p, tags, p.getCreator()));
  }

  /**
   * 这里使用jsoup,因为jaxb必须要求well-formed格式的xml文档
   * 
   * @param html
   * @param wpdomain
   *          原始wordpress域名
   * @param links
   * @return
   */
  private static String htmlImageOrLinkAdjust(String html, String wpdomain, Map<String, Upload> links){
    Document doc = Jsoup.parse(html);
    OutputSettings settings = new OutputSettings();
    settings.prettyPrint(false);
    doc.outputSettings(settings);
    Elements eles = doc.select("img,a");
    for(int i = 0; i < eles.size(); i++){
      Element ele = eles.get(i);
      String tagName = ele.nodeName();
      String linkAttr = "img".equals(tagName) ? "src" : "href";
      String oldLink = ele.attr(linkAttr);
      if(!oldLink.startsWith("http://") && !oldLink.startsWith("https://")){
        oldLink = wpdomain + oldLink;
      }
      Upload newUpload = links.get(oldLink);
      if(newUpload != null){
        ele.attr(linkAttr, WebConstants.getDomainLink(newUpload.getPath()));
      }
    }

    return doc.body().html();
  }
}

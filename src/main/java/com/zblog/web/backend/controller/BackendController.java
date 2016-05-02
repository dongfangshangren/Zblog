package com.zblog.web.backend.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.zblog.biz.CommentManager;
import com.zblog.biz.PostManager;
import com.zblog.core.Constants;
import com.zblog.core.dal.constants.PostConstants;
import com.zblog.core.dal.entity.User;
import com.zblog.core.plugin.MapContainer;
import com.zblog.core.util.CookieUtil;
import com.zblog.core.util.ServletUtils;
import com.zblog.core.util.StringUtils;
import com.zblog.service.CommentService;
import com.zblog.service.PostService;
import com.zblog.service.UploadService;
import com.zblog.service.UserService;
import com.zblog.service.shiro.StatelessToken;
import com.zblog.service.vo.OSInfo;
import com.zblog.web.backend.form.LoginForm;
import com.zblog.web.backend.form.validator.LoginFormValidator;
import com.zblog.web.support.CookieRemberManager;
import com.zblog.web.support.WebContextFactory;

@Controller
@RequestMapping("/backend")
public class BackendController{
  @Autowired
  private UserService userService;
  @Autowired
  private PostManager postManager;
  @Autowired
  private CommentManager commentManager;
  @Autowired
  private PostService postService;
  @Autowired
  private CommentService commentService;
  @Autowired
  private UploadService uploadService;

  @RequiresRoles(value = { "admin", "editor" }, logical = Logical.OR)
  @RequestMapping(value = "/index", method = RequestMethod.GET)
  public String index(Model model){
    model.addAttribute("osInfo", OSInfo.getCurrentOSInfo());

    /* 基本站点统计信息 */
    model.addAttribute("userCount", userService.count());
    model.addAttribute("postCount", postService.count());
    model.addAttribute("commentCount", commentService.count());
    model.addAttribute("uploadCount", uploadService.count());

    model.addAttribute("posts", postManager.listRecent(10, PostConstants.POST_CREATOR_ALL));
    model.addAttribute("comments", commentManager.listRecent());
    return "backend/index";
  }

  @RequestMapping(value = "/login", method = RequestMethod.GET)
  public String login(String msg, Model model){
    if(WebContextFactory.get().isLogon())
      return "redirect:/backend/index";

    if("logout".equals(msg)){
      model.addAttribute("msg", "您已登出。");
    }else if("unauthenticated".equals(msg)){
      model.addAttribute("msg", "你没有当前操作权限");
    }
    return "backend/login";
  }

  @RequestMapping(value = "/logout")
  public String logout(HttpServletRequest request, HttpServletResponse response){
    CookieRemberManager.logout(request, response);
    SecurityUtils.getSubject().logout();
    CookieUtil cookieUtil = new CookieUtil(request, response);
    cookieUtil.removeCokie(Constants.COOKIE_CSRF_TOKEN);
    cookieUtil.removeCokie("comment_author");
    cookieUtil.removeCokie("comment_author_email");
    cookieUtil.removeCokie("comment_author_url");
    
    return "redirect:/backend/login?msg=logout";
  }

  @RequestMapping(value = "/login", method = RequestMethod.POST)
  public String dashboard(LoginForm form, HttpServletRequest request, HttpServletResponse response){
    MapContainer result = LoginFormValidator.validateLogin(form);
    if(!result.isEmpty()){
      request.setAttribute("msg", result.get("msg"));
      return "backend/login";
    }

    User user = userService.login(form.getUsername(), form.getPassword());
    if(user == null){
      request.setAttribute("msg", "用户名密码错误");
      return "backend/login";
    }

    SecurityUtils.getSubject().login(new StatelessToken(user.getId(), user.getPassword()));
    CookieUtil cookieUtil = new CookieUtil(request, response);
    /* 根据RFC-2109中的规定，在Cookie中只能包含ASCII的编码 */
    cookieUtil.setCookie(Constants.COOKIE_USER_NAME, form.getUsername(), false, 7 * 24 * 3600);
    cookieUtil.setCookie("comment_author", user.getNickName(), "/", false, 365 * 24 * 3600);
    cookieUtil.setCookie("comment_author_email", user.getEmail(), "/", false, 365 * 24 * 3600, false);
    cookieUtil.setCookie("comment_author_url", ServletUtils.getDomain(request), "/", false, 365 * 24 * 3600, false);

    CookieRemberManager.loginSuccess(request, response, user.getId(), user.getPassword(), form.isRemeber());

    return "redirect:" + StringUtils.emptyDefault(form.getRedirectURL(), "/backend/index");
  }

}

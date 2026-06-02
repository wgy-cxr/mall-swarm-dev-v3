package com.macro.mall.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.constant.AuthConstant;
import com.macro.mall.util.StpMemberUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;

import java.util.*;

/*
* 登录鉴权逻辑：首先服务启动时，在mall-admin中有个
* @Component
public class PathResourceRulesHolder {

    @Autowired
    private UmsResourceService resourceService;

    @PostConstruct  // Spring 容器启动时自动执行
    public void initPathResourceMap(){
        resourceService.initPathResourceMap();
    }
}在这个bean（这个bean没有用，就是为了执行前置的这个逻辑）初始化的时候，会吧哪个路径需要哪个权限这个关系存到redis中，然后开始登录，首先如果是登录请求
* 就不会被拦截，直接执行登录逻辑，登录逻辑是先去数据库查密码，如果没有，就失败，密码不对也失败，等等，然后吧用户信息存到redis中，其中这个用户拥有的权限也会被存到redis中
* 然后登录成功之后，会返回一个token,这个token能解析出用户ID，用户登录类型等。
* 这时候登陆成功了，此时在进行别的请求，被拦截后，如果是后台管理者，就会鉴权（前台用户不用鉴权，因为啥权限也没有）。先去看这个路径需要哪些权限。然后看这个用户有没有这个权限
*    // 接口需要权限时鉴权
                    if(CollUtil.isNotEmpty(needPermissionList)){
                        SaRouter.match(requestPath, r -> StpUtil.checkPermissionOr(Convert.toStrArray(needPermissionList)));
                    }
                    if中的方法会调用STPINTERFACEIMPL中的方法去获取当前用户存到redis中的权限，然后根据这个权限去数据库中查询这个权限对应的资源，然后返回给前端
*
* */
@Configuration
public class SaTokenConfig {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 注册Sa-Token全局过滤器
     */
    @Bean
    public SaReactorFilter getSaReactorFilter(IgnoreUrlsConfig ignoreUrlsConfig) {
        return new SaReactorFilter()
                // 拦截地址
                .addInclude("/**")
                // 配置白名单路径
                .setExcludeList(ignoreUrlsConfig.getUrls())
                // 鉴权方法：每次访问进入
                .setAuth(obj -> {
                    // 对于OPTIONS预检请求直接放行
                    SaRouter.match(SaHttpMethod.OPTIONS).stop();
                    // 登录认证：商城前台会员认证
                    SaRouter.match("/mall-portal/**", r -> StpMemberUtil.checkLogin()).stop();
                    // 登录认证：管理后台用户认证
                    SaRouter.match("/mall-admin/**", r -> StpUtil.checkLogin());
                    // 权限认证：管理后台用户权限校验
                    // 获取Redis中缓存的各个接口路径所需权限规则
                    Map<Object, Object> pathResourceMap = redisTemplate.opsForHash().entries(AuthConstant.PATH_RESOURCE_MAP);
                    // 获取到访问当前接口所需权限（一个路径对应多个资源时，拥有任意一个资源都可以访问该路径）
                    List<String> needPermissionList = new ArrayList<>();
                    // 获取当前请求路径
                    String requestPath = SaHolder.getRequest().getRequestPath();
                    // 创建路径匹配器
                    PathMatcher pathMatcher = new AntPathMatcher();
                    Set<Map.Entry<Object, Object>> entrySet = pathResourceMap.entrySet();
                    for (Map.Entry<Object, Object> entry : entrySet) {
                        String pattern = (String) entry.getKey();
                        if (pathMatcher.match(pattern, requestPath)) {
                            needPermissionList.add((String) entry.getValue());
                        }
                    }
                    // 接口需要权限时鉴权
                    if(CollUtil.isNotEmpty(needPermissionList)){
                        SaRouter.match(requestPath, r -> StpUtil.checkPermissionOr(Convert.toStrArray(needPermissionList)));
                    }
                })
                // setAuth方法异常处理
                .setError(this::handleException);
    }

    /**
     * 自定义异常处理
     */
    private CommonResult handleException(Throwable e) {
        //设置错误返回格式为JSON
        ServerWebExchange exchange = SaReactorSyncHolder.getContext();
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Cache-Control","no-cache");
        CommonResult result = null;
        if(e instanceof NotLoginException){
            result = CommonResult.unauthorized(null);
        }else if(e instanceof NotPermissionException){
            result = CommonResult.forbidden(null);
        }else{
            result = CommonResult.failed(e.getMessage());
        }
        return result;
    }
}


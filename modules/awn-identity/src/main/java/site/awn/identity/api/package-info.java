/**
 * 身份模块对外接口：其他模块只允许依赖本包，
 * domain / infrastructure / application 均为模块内部。
 * 买家侧 API 应用层（awn-api）经由本命名接口接入登录与用户视图。
 */
@org.springframework.modulith.NamedInterface("identity-api")
package site.awn.identity.api;

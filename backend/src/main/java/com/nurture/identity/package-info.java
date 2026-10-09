/**
 * 身份认证域（identity模块）。
 *
 * <p>领域职责：统一账号体系、密码加密、JWT / API Token 签发与上下文校验。</p>
 *
 * <p>边界约束：本模块仅可经由本包内 api 应用服务与领域事件对外协作，
 * 其他模块严禁直接访问本模块仓储与实体。</p>
 */
package com.nurture.identity;

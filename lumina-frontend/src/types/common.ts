/**
 * 通用类型定义
 */

/**
 * 通用键值对
 */
export type Recordable<T = any> = Record<string, T>

/**
 * 函数类型
 */
export type Fn = (...args: any[]) => any

/**
 * Promise 函数类型
 */
export type PromiseFn = (...args: any[]) => Promise<any>

/**
 * 阶段字符串
 */
export type Stage = '1' | '2' | '3'

/**
 * 分割字符串
 */
export type Splittable<T extends string> = T extends `${infer A}.${infer B}` ? A | B : T

/**
 * 移除 readonly
 */
export type Mutable<T> = {
  -readonly [P in keyof T]: T[P]
}

/**
 * 移除函数签名
 */
export type OmitFn<T> = Omit<T, keyof Function>

/**
 * 提取函数参数类型
 */
export type ArgumentsType<T> = T extends (...args: infer P) => any ? P : never

/**
 * 深度 Partial
 */
export type DeepPartial<T> = {
  [P in keyof T]?: T[P] extends object ? DeepPartial<T[P]> : T[P]
}

/**
 * 深度 Readonly
 */
export type DeepReadonly<T> = {
  readonly [P in keyof T]: T[P] extends object ? DeepReadonly<T[P]> : T[P]
}

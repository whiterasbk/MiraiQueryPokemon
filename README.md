# QueryPokemon
宝可梦查询插件


![](https://img.shields.io/github/downloads/whiterasbk/MiraiQueryPokemon/total)
![](https://img.shields.io/github/v/release/whiterasbk/MiraiQueryPokemon?display_name=tag)
![](https://img.shields.io/github/languages/top/whiterasbk/MiraiQueryPokemon)
![GitHub](https://img.shields.io/github/license/whiterasbk/MiraiQueryPokemon)

## 效果
![](https://mirai.mamoe.net/assets/uploads/files/1660625712920-1281fb2f-9c18-4855-a44a-1d8879ec17ae-image.png)

## 安装方法
1. 将插件放入 plugins 文件夹
2. 运行一次或在 data 文件夹创建 bot.good.QueryPokemon 文件夹
3. 将 release 中的 query-script.zip 解压并放入 bot.good.QueryPokemon 中
4. 在管理员前提下, 发送 #enable 和 #enable this all
5. 若 jdk 版本大于 11 则需在 `plugin-shared-libraries/libraries.txt` 追加以下内容
```text
org.openjdk.nashorn:nashorn-core:15.4
```
6. `query` 命令需要使用 chatcommand 插件作为前置
## 使用方法
支持命令

- #图鉴 名称/id [第几个形态]
- #道具 名称/id 
- #招式 名称/id 
- #特性 名称/id 

## 自定义
data/bot.good.QueryPokemon/ 目录下的 *.query 文件是模板文件, 可修改格式
# ThemeBinding 使用文档

## **前言**

公司目前绝大部分项目都需要切换主题，每次项目到了切换主题相关工作的时候都会听到不少抱怨的声音（包括我自己）：麻烦、纯体力、机械，我相信做过主题切换的同事不少都有类似的感受。如果能有个自动切换资源的框架就好了！本篇方案就诞生于这样的背景。

本方案基于原QGAPI的主题切换流程[APP(通用): 主题方案\用户\升降屏v2.1](https://pateo.feishu.cn/docs/doccnNFg8Yb9EwnG8rljuAaRvfb) ，资源存放位置，如何打包还是基于这篇文档。当处理好资源相关工作后，剩下的工作由ThemeBinding接管。并且**集成非常简单，其主要原理是基于APT自动生成所需代码，只需要添加相应的注解即可**。

## **集成方法**

1. 添加依赖：

首先要添加公司Maven仓库地址

```
repositories {
    maven { url "http://10.10.96.219:8081/repository/nj-pateo/" }
    google()
    jcenter()
}
```

然后在module下添加下列依赖

```
implementation 'com.pateo.theme:theme-binding:0.0.1'
kapt 'com.pateo.theme:theme-processor:0.0.2'
```

1. 在对应的资源属性上添加注解

目前可用的注解有两个：`**BindColor、BindDrawable**`

```
@BindColor(R.color.common_text) int commonColor;
@BindDrawable(R.drawable.drawable) Drawable drawable;
@BindColor(R.color.common_text) lateinit var commonColor:Int
@BindDrawable(R.drawable.drawable) lateinit var drawable:Drawable
```

ThemeBinding的基本功能是获取对应主题的color、drawable并赋值到对应的变量。如果变量类型是LiveData则会自动更新livedata数据，所以推荐使用LiveData配合DataBinding使用。我们可以在任何类中使用相关注解，Activity、Fragment、其他类都行，只要能引用到就行。不过原则上建议使用一个或多个工具类专门负责管理主题资源，这样结构会比较清晰。



## **示例代码**

```
public class MyTheme {
  @BindColor(R.color.common_text) MutableLiveData<Integer> commonColor;
  @BindDrawable(R.drawable. backgroud) MutableLiveData<Drawable> commonDrawable;
}
class MyActivity{
    public void onCreate(Bundle savedInstanceState) {
        ......
        dataBinding.setTheme(new MyTheme()) //MyTheme也可以处理成单例
        ......
    }
}
object MyTheme {
    @BindColor(R.color.common_text)
    lateinit var commonColor: MutableLiveData<Int>
    @BindColor(R.drawable.backgroud)
    lateinit var commonDrawable = MutableLiveData<Drawable>()
}

class MyActivity{
    override fun onCreate(savedInstanceState: Bundle?) {
        ......
        dataBinding.setTheme(MyTheme)
        ......
    }
}
<layout>
    <data>
        <variable
            name="theme"
            type="***.***.MyTheme" />
    </data>
    <ImageView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:src="@{theme.commonDrawable}"/>
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        android:text="my name"
        android:textColor="@{theme.commonColor}"
        android:textSize="32sp"/>
</layout>
```

打完收工！

如上示例代码，我们在配置主题时主要工作只需要把资源变量配上对应的注解即可。

目前只支持Color、Drawable两种类型的绑定，如果大家有什么问题或建议都可以提出来。
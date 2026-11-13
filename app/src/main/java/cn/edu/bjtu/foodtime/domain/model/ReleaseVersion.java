package cn.edu.bjtu.foodtime.domain.model;

/** Display stages never decide upgrade order; Android versionCode remains authoritative. */
public final class ReleaseVersion {
  public static boolean testing(String name) {
    return name!=null&&!name.isBlank()&&!name.matches("[0-9]+\\.[0-9]+\\.[0-9]+");
  }
  public static String display(String name) {return "v"+name+(testing(name)?"（测试版）":"");}
  public static boolean forced(Models.AppRelease release,int installedCode) {
    return !testing(release.versionName())&&release.minVersionCode()>installedCode;
  }
  private ReleaseVersion(){}
}

import com.nmssaveexplorer.IconRegistry;
import javafx.application.Application;
import javafx.stage.Stage;
public class QuickIconCheck extends Application {
  @Override public void start(Stage stage) {
    System.out.println(IconRegistry.getIcon("CASING"));
    System.out.println(IconRegistry.getIcon("HOT1"));
    System.exit(0);
  }
  public static void main(String[] args){launch(args);} }

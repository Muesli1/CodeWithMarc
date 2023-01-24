package cfm;

import java.io.File;

public class StressTest {

    public static void main(String[] args) {

        Client.STRESS_TEST = true;

        if(args.length != 2) {
            return;
        }

        File mainDirectory = new File(args[0]);

        if(mainDirectory.exists() == false || mainDirectory.isDirectory() == false) {
            throw new RuntimeException();
        }

        String name = args[1];

        for (int num = 0; num < 10; num++) {
            int finalNum = num;
            Thread r = new Thread(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Started " + finalNum);
                    create(mainDirectory, name, finalNum);
                }
            });
            r.start();


        }

        try {
            Thread.sleep(1000000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.exit(23);

    }

    private static void create(File mainDirectory, String name, int num) {
        File dir = new File(mainDirectory, "" + num);
        if(dir.exists()) {
            //throw new RuntimeException("Already exists: " + num);
        }
        else {
            dir.mkdir();
            if (dir.exists() == false) {
                throw new RuntimeException("Could not create directory: " + num);
            }
        }

        Client.main(new String[] {"false", "NULL", name, dir.toString()});


    }

}

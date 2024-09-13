package Basics.EndTermCode;

class DataTypes {
    public static void main(String[] args) {
        double a;
        int b;
        a = 10.76;
        b = 2;
        System.out.println("a =\n " + a);
        System.out.println("b =\n " + b);

        /*
        System.out.print("Enter c ");
        Scanner s = new Scanner(System.in);
        int c = s.nextInt();
        System.out.println("c = " + c);
        double d = a++ + c;
        System.out.println("a + c = " + d);
        */

        if (a < b) {
            System.out.println("a is less than b");
        } else {
            System.out.println("a is greater than b");
        }

        for (int i = 1; i <= 10; i++) {
            System.out.println("i = " + i);
        }

        while (b < 10) {
            System.out.println("b = " + b);
            b += 1;
            ;
        }

        System.out.print(1+','+1);
    }


}


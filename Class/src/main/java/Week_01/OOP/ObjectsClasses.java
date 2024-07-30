package OOP;

public class ObjectsClasses {
    public static void main(String[] args){
        /*Person p = new Person();
        p.name="Xenom";
        p.age=50;
        p.salary=50000;
        p.weight=60;
        p.height=5;

        System.out.println(p.ComputeBMI());*/

        Person Mantu=new Person("Mantu", 26, 42000, 63, 5);
        Person Xeno=new Person("Xenom", 32, 60000, 71, 6);

        System.out.println(Mantu.salary);

        //function to compare salary
        Mantu.CompareSalary(Xeno);
    }
}
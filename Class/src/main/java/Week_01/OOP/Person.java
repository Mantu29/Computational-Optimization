package OOP;

public class Person {
    public String name;
    public int age;
    public int salary;
    public double weight;
    public double height;

    public double ComputeBMI(){
        double i = this.age / this.weight;
        return i;
    }

    public void CompareSalary(Person p){
        if(p.salary > this.salary){
            System.out.println(p.name + " is richer");
        }
    }

    public Person(String name, int age, int salary, double weight, double height){
        this.name=name;
        this.age=age;
        this.salary=salary;
        this.weight=weight;
        this.height=height;
    }
}
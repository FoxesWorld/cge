package org.foxesworld.cge.ue.model;


/**
 * Представление FName — в общем виде это индекс в NameMap + number.
 * Конкретные реализации engine-версий могут поменяться, поэтому мы храним rawIndex/number
 * и разрешаем позже получить строковое значение через UPackage.lookupName(index).
 */
public class FName {
    public final int index;
    public final int number;

    public FName(int index, int number) {
        this.index = index;
        this.number = number;
    }

    @Override
    public String toString() {
        return "FName(idx=" + index + ", num=" + number + ")";
    }
}
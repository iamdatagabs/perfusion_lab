// PerfusionLab — schematic, parametric CEC circuit assembly
// Educational visualization only. Not dimensionally accurate or clinically validated.
$fn = 48;
tube_radius = 3.2;
reservoir_h = 62;
reservoir_r = 18;
oxygenator_h = 54;
oxygenator_r = 15;
pump_r = 19;
pump_depth = 14;

module tube_between(a, b, r=tube_radius) {
    v = [b[0]-a[0], b[1]-a[1], b[2]-a[2]];
    length = norm(v);
    axis = [-v[1], v[0], 0];
    angle = (norm(axis) < 0.0001) ? (v[2] >= 0 ? 0 : 180) : acos(v[2]/length);
    translate(a) rotate(a=angle, v=(norm(axis) < 0.0001 ? [1,0,0] : axis))
        cylinder(h=length, r=r);
}
module reservoir() {
    color([0.25,0.68,0.78,0.72]) union() {
        cylinder(h=reservoir_h, r=reservoir_r);
        translate([0,0,reservoir_h]) sphere(r=reservoir_r);
        cylinder(h=5, r=reservoir_r*0.82);
    }
}
module pump_head() {
    color([0.78,0.55,0.30]) difference() {
        rotate([90,0,0]) cylinder(h=pump_depth, r=pump_r, center=true);
        rotate([90,0,0]) cylinder(h=pump_depth+1, r=pump_r-3, center=true);
    }
    color([0.92,0.78,0.48]) rotate([90,0,0]) cylinder(h=2, r=pump_r*0.55, center=true);
}
module oxygenator() {
    color([0.66,0.76,0.86,0.78]) union() {
        cylinder(h=oxygenator_h, r=oxygenator_r);
        translate([0,0,oxygenator_h]) cylinder(h=5, r1=oxygenator_r, r2=oxygenator_r*0.75);
        for (z=[7:5:oxygenator_h-5]) translate([0,0,z]) cylinder(h=0.7, r=oxygenator_r*0.91);
    }
}
module cannula(length=35, r=2.4) {
    color([0.78,0.82,0.86]) union() {
        cylinder(h=length, r=r);
        translate([0,0,length-5]) cylinder(h=5, r1=r, r2=r*1.55);
    }
}
translate([-62,0,0]) reservoir();
translate([-18,0,24]) pump_head();
translate([34,0,0]) oxygenator();
tube_between([-44,0,48],[-31,0,48],2.5);
tube_between([-5,0,24],[19,0,24],2.5);
tube_between([34,0,oxygenator_h+3],[70,0,oxygenator_h+3],2.5);
tube_between([70,0,oxygenator_h+3],[70,0,18],2.5);
tube_between([70,0,18],[-62,0,18],2.5);
translate([-62,0,reservoir_h+2]) cannula();
translate([34,0,oxygenator_h+8]) rotate([90,0,0]) cannula(30,2.2);

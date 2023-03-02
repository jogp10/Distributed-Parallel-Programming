use std::cmp::min;
use std::io;

fn on_mult(m_ar : usize, m_br : usize){
    let mut temp: f64;

    let pha = vec![1.0; m_ar*m_ar];
    let mut phb = vec![0.0; m_br*m_br];
    let mut phc = vec![0.0; m_ar*m_br];

    for i in 0..m_br {
        for j in 0..m_br {
            phb[i*m_br + j] = (i+1) as f64;
        }
    }

    let time1 = std::time::Instant::now();

    for i in 0..m_ar {
        for j in 0..m_br {
            temp = 0.0;
            for k in 0..m_ar {
                temp += pha[i*m_ar+k] * phb[k*m_br+j];
            }
            phc[i*m_ar+j]=temp;
        }
    }

    let time2 = std::time::Instant::now();
    println!("Time elapsed is: {:?}", time2.duration_since(time1));

    println!("Result matrix: ");
    for i in 0..1 {
        for j in 0..min(10,m_br) {
            println!("{} ", phc[j]);
        }
    }
    println!();
}

fn on_mult_line(m_ar : usize, m_br : usize){
    let mut temp: f64;

    let pha = vec![1.0; m_ar*m_ar];
    let mut phb = vec![0.0; m_br*m_br];
    let mut phc = vec![0.0; m_ar*m_br];

    for i in 0..m_br {
        for j in 0..m_br {
            phb[i*m_br + j] = (i+1) as f64;
        }
    }

    let time1 = std::time::Instant::now();

    for i in 0..m_ar {
        for k in 0..m_ar {
            temp = pha[i * m_ar + k];
            for j in 0..m_br {
                phc[i * m_ar + j] += temp * phb[k * m_br + j];
            }
        }
    }

    let time2 = std::time::Instant::now();
    println!("Time elapsed is: {:?}", time2.duration_since(time1));

    // display 10 elements of the result matrix to verify correctness
    println!("Result matrix: ");
    for i in 0..1 {
        for j in 0..min(10,m_br) {
            println!("{} ", phc[j]);
        }
    }
    println!();

}

fn on_mult_block(m_ar : usize, m_br : usize, block_size : usize){
    let mut temp: f64;

    let pha = vec![1.0; m_ar*m_ar];
    let mut phb = vec![0.0; m_br*m_br];
    let mut phc = vec![0.0; m_ar*m_br];

    for i in 0..m_br {
        for j in 0..m_br {
            phb[i*m_br + j] = (i+1) as f64;
        }
    }

    let time1 = std::time::Instant::now();

    //divide the matrix in blocks of block_size x block_size
    for i in (0..m_ar).step_by(block_size) {
        for j in( 0..m_br).step_by(block_size) {
            for k in (0..m_ar).step_by(block_size) {
                for ii in i..min(i+block_size,m_ar) {
                    for kk in k..min(k+block_size,m_ar) {
                        temp = pha[ii * m_ar + kk];
                        for jj in j..min(j+block_size,m_br) {
                            phc[ii * m_ar + jj] += temp * phb[kk * m_br + jj];
                        }
                    }
                }
            }
        }
    }

    let time2 = std::time::Instant::now();
    println!("Time elapsed is: {:?}", time2.duration_since(time1));

    println!("Result matrix: ");
    for i in 0..1 {
        for j in 0..min(10,m_br) {
            println!("{} ", phc[j]);
        }
    }
    println!();

}

fn main() {
    let stdin = io::stdin();
    let mut input = String::new();
    let mut op = 1;
    let mut lin = 0;
    let mut col = 0;
    let mut block_size = 0;

    while op != 0 {
        println!("1. Multiplication");
        println!("2. Line Multiplication");
        println!("3. Block Multiplication");
        println!("Selection?: ");
        let mut input = String::new();
        stdin.read_line(&mut input).unwrap();
        op = input.trim().parse().unwrap();
        if op == 0 {
            break;
        }
        println!("Dimensions: lins=cols ? ");
        let mut input = String::new();
        stdin.read_line(&mut input).unwrap();

        lin = input.trim().parse().unwrap();
        col = lin;

        match op {
            1 => on_mult(lin, col),
            2 => on_mult_line(lin, col),
            3 => {
                println!("Block Size? ");
                let mut input = String::new();
                stdin.read_line(&mut input).unwrap();
                block_size = input.trim().parse().unwrap();
                on_mult_block(lin, col, block_size);
            }
            _ => println!("Invalid option"),
        }
    }
}




const b = [1, 2, 3].reduce(item => item * 3);
console.log(b);
const c = async () => {
    const d = await function () {
        return setTimeout(() => 20, 1000)
    }();
    console.log(d)
}
c()
import datetime
import os
import platform
import subprocess
import shutil
import sys
from enum import Enum
from pathlib import Path

MODULE_ROOT = Path(os.path.dirname(os.path.abspath(__file__)))
PROJECT_ROOT = MODULE_ROOT.parent
RESSOURCE_SUBPATH = Path("src/main/resources/org/openlca/nativelib")


class OS(str, Enum):
    MACOS_ARM = "macos-arm64"
    MACOS_X86_64 = "macos-x86_64"
    WINDOWS = "windows-x86_64"
    LINUX = "linux-x86_64"

    def __repr__(self):
        return "OS." + self.name

    def short(self):
        if self == OS.MACOS_ARM or self == OS.MACOS_X86_64:
            return "macos"
        if self == OS.WINDOWS:
            return "windows"
        if self == OS.LINUX:
            return "linux"


class Node:

    def __init__(self, path: str, name: str):
        self.path = path
        self.name = name
        self.deps = []


def get_os() -> OS:
    platform_system = platform.system().lower()
    if platform_system == "windows":
        return OS.WINDOWS
    platform_platform = platform.platform()
    if platform_system == "linux":
        return OS.LINUX
    if platform_system == "darwin":
        if "arm" in platform_platform:
            return OS.MACOS_ARM
        if "x86_64" in platform_platform:
            return OS.MACOS_X86_64

    sys.exit("unknown platform: " + platform_system)


def get_lib_ext() -> str:
    _os = get_os()
    if _os == OS.LINUX:
        return ".so"
    if _os == OS.MACOS_X86_64 or _os == OS.MACOS_ARM:
        return ".dylib"
    if _os == OS.WINDOWS:
        return ".dll"
    sys.exit("unknown os: " + _os)


def as_lib(name: str) -> str:
    _os = get_os()
    prefix = ""
    if _os != OS.WINDOWS:
        if not name.startswith("lib"):
            prefix = "lib"
    return prefix + name + get_lib_ext()


def get_julia_libdir():
    """Read the Julia library path from the config file. """
    _os = get_os()
    libdir = None
    config = os.path.join(MODULE_ROOT, "config")
    with open(config, "r", encoding="utf-8") as f:
        libdir_key = _os.short() + "-julia-lib-dir"
        for line in f.readlines():
            parts = line.split("=")
            if len(parts) < 2:
                continue
            key = parts[0].strip()
            if key != libdir_key:
                continue
            libdir = parts[1].strip()
            break
    if libdir is None:
        sys.exit(
            "could not read Julia lib folder for OS=%s from config" % _os.value)
    return libdir


def get_version():
    """Read the version of the library from the Cargo.toml file."""
    with open("Cargo.toml", "r", encoding="utf-8") as f:
        for line in f.readlines():
            if not line.startswith("version"):
                continue
            return line.split("=")[1].strip().strip("\"")


def get_deps(lib_file: str, libs: list) -> list:
    _os = get_os()
    cmd = None
    if _os == OS.MACOS_ARM or OS.MACOS_ARM:
        cmd = ["otool", "-L", lib_file]
    if _os == OS.WINDOWS:
        cmd = ["Dependencies.exe", "-imports", lib_file]
    if _os == OS.LINUX:
        cmd = ["ldd", lib_file]
    if cmd is None:
        sys.exit("no deps command for os " + _os.value)

    # in Python 3.7 we have capture_output and text flags
    # but we make this compatible with Python 3.6 here
    proc = subprocess.run(cmd, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE)
    out = None
    if proc.stdout is not None:
        out = proc.stdout.decode(sys.stdout.encoding)
    elif proc.stderr is not None:
        out = proc.stderr.decode(sys.stderr.encoding)
    if out is None:
        return []
    deps = set()
    for line in out.splitlines():
        for lib in libs:
            if lib in lib_file:
                continue
            if lib not in line:
                continue
            # make sure that the name of the
            # library is not a part of another
            # library name that is also contained
            # in the line (e.g. `libcamd.so` and
            # `libcamd.so.2`)
            dep = lib
            for other in libs:
                if other == dep:
                    continue
                if dep not in other:
                    continue
                if other not in line:
                    continue
                dep = other
            deps.add(dep)
    return list(deps)


def get_dep_dag(entry: str) -> Node:
    """Create the directed acyclic graph (DAG) of the dependencies. """
    libdir = get_julia_libdir()
    libs = os.listdir(libdir)
    handled = set()
    root = Node(entry, entry.split(os.path.sep)[-1])
    queue = [root]
    while len(queue) != 0:
        n = queue.pop(0)  # type: Node
        for dep in get_deps(n.path, libs):
            dep_node = Node(os.path.join(libdir, dep), dep)
            n.deps.append(dep_node)
            if dep in handled:
                continue
            handled.add(dep)
            queue.append(dep_node)
    return root


def topo_sort(dag: Node) -> list:
    """Creates a topological order of the nodes' dependency DAG in increasing
       dependency order."""
    in_degrees = {}
    dependents = {}
    queue = [dag]
    handled = set()
    while len(queue) != 0:
        n = queue.pop(0)  # type: Node
        if n.name in handled:
            continue
        handled.add(n.name)
        if n.name not in in_degrees:
            in_degrees[n.name] = 0
        for dep in n.deps:
            queue.append(dep)
            if dep.name not in in_degrees:
                in_degrees[dep.name] = 0
            depl = dependents.get(dep.name)
            if depl is None:
                depl = []
                dependents[dep.name] = depl
            depl.append(n.name)
            in_degrees[n.name] = in_degrees[n.name] + 1

    ordered = []
    while len(in_degrees) != 0:

        lib = None
        for _lib, _indeg in in_degrees.items():
            if _indeg == 0:
                lib = _lib
                break
        if lib is None:
            sys.exit("could not calculate dependency order;"
                     + " are there cycles in the dependencies?")

        ordered.append(lib)
        in_degrees.pop(lib)
        depl = dependents.pop(lib, None)
        if depl is None:
            continue
        for dependent in depl:
            in_degrees[dependent] = in_degrees[dependent] - 1

    return ordered


def viz():
    wiumf = os.path.join(MODULE_ROOT, "bin", as_lib("olcar_withumf"))
    if not os.path.exists(wiumf):
        sys.exit(wiumf + " does not exist")
    dag = get_dep_dag(wiumf)
    print("digraph g {")
    queue = [dag]
    while len(queue) != 0:
        n = queue.pop(0)
        for dep in n.deps:
            print('  "%s" -> "%s";' % (n.name, dep.name))
            queue.append(dep)
    print("}")


def collect() -> list:
    """Collect all dependencies in a list."""
    wiumf = os.path.join(MODULE_ROOT, "bin", as_lib("olcar_withumf"))
    if not os.path.exists(wiumf):
        sys.exit(wiumf + " does not exist")
    dag = get_dep_dag(wiumf)
    libs = topo_sort(dag).copy()
    woumf = os.path.join(MODULE_ROOT, "bin", as_lib("olcar"))
    if not os.path.exists(woumf):
        sys.exit(woumf + " does not exist")
    for lib in topo_sort(get_dep_dag(woumf)):
        if lib not in libs:
            libs.append(lib)
    return libs


def sync():
    print("Sync libraries with the corresponding submodules.")
    libs = collect()
    julia_dir = get_julia_libdir()
    for lib in libs:
        module_name = 'olca-native-umfpack-' + get_os().value
        target = PROJECT_ROOT / module_name / RESSOURCE_SUBPATH / lib
        if os.path.exists(target):
            print(f"Target ({target}) exists")
            continue
        source = os.path.join(julia_dir, lib)
        if not os.path.exists(source):
            print(f"ERROR: {source} does not exist")
            continue
        shutil.copyfile(source, target)
        print(f"copied {target}")


def dist():
    print("create the distribution package")
    sync()

    shutil.rmtree("dist", ignore_errors=True)
    now = datetime.datetime.now()
    suffix = "_%s_%s_%d-%02d-%02d" % (
        get_version(), get_os().value, now.year, now.month, now.day)

    # with umfpack
    zip_file = os.path.join("dist", "olcar_withumf" + suffix)
    print("create package " + zip_file)
    wiumf = os.path.join(MODULE_ROOT, "bin", as_lib("olcar_withumf"))
    libs = topo_sort(get_dep_dag(wiumf))
    os.makedirs("dist/wi_umfpack")
    for lib in libs:
        shutil.copyfile(os.path.join("bin", lib),
                        os.path.join("dist", "wi_umfpack", lib))
    shutil.copyfile("LICENSE.md", "dist/wi_umfpack/LICENSE.md")
    shutil.make_archive(zip_file, "zip", "dist/wi_umfpack")

    # without umfpack
    zip_file = os.path.join("dist", "olcar" + suffix)
    print("create package " + zip_file)
    woumf = os.path.join(MODULE_ROOT, "bin", as_lib("olcar"))
    libs = topo_sort(get_dep_dag(woumf))
    os.makedirs("dist/wo_umfpack")
    for lib in libs:
        shutil.copyfile(os.path.join("bin", lib),
                        os.path.join("dist", "wo_umfpack", lib))
    shutil.copyfile("LICENSE.md", "dist/wo_umfpack/LICENSE.md")
    shutil.make_archive(zip_file, "zip", "dist/wo_umfpack")


def java():
    _os = repr(get_os())

    wiumf = os.path.join(MODULE_ROOT, "bin", as_lib("olcar_withumf"))
    libs = topo_sort(get_dep_dag(wiumf))

    print("if (os == %s) {" % _os)
    print("  if (opt == LinkOption.ALL) {")
    print("    return new String[] {")
    for lib in libs:
        print("      \"%s\"," % lib)
    print("    };")

    woumf = os.path.join(MODULE_ROOT, "bin", as_lib("olcar"))
    libs = topo_sort(get_dep_dag(woumf))
    print("  } else {")
    print("    return new String[] {")
    for lib in libs:
        print("      \"%s\"," % lib)
    print("    };")
    print("  }")
    print("}")


def index():
    """Create the index.txt file in the os-specific maven submodules."""
    _os = get_os()

    wiumf = os.path.join(MODULE_ROOT, "bin", as_lib("olcar_withumf"))
    libs = topo_sort(get_dep_dag(wiumf))
    module_name = 'olca-native-umfpack-' + _os.value
    index = PROJECT_ROOT / module_name / RESSOURCE_SUBPATH / "index.txt"

    with open(index, "w") as file:
        for lib in libs:
            file.write(lib + "\n")
    print(f"{index.name} file created in {index.parent}")

    woumf = os.path.join(MODULE_ROOT, "bin", as_lib("olcar"))
    libs = topo_sort(get_dep_dag(woumf))
    module_name = 'olca-native-blas-' + _os.value
    index = PROJECT_ROOT / module_name / RESSOURCE_SUBPATH / "index.txt"

    with open(index, "w") as file:
        for lib in libs:
            file.write(lib + "\n")
    print(f"{index.name} file created in {index.parent}")


def clean():
    shutil.rmtree("./bin", ignore_errors=True)
    os.mkdir("./bin")
    shutil.rmtree("./dist", ignore_errors=True)
    os.mkdir("./dist")


def main():
    args = sys.argv
    if len(args) < 2:
        print(collect())
        return
    cmd = args[1]
    if cmd == "viz":
        viz()
    elif cmd == "collect":
        print(collect())
    elif cmd == "sync":
        sync()
    elif cmd == "dist":
        dist()
    elif cmd == "java":
        java()
    elif cmd == "index":
        index()
    elif cmd == "clean":
        clean()


if __name__ == '__main__':
    main()
